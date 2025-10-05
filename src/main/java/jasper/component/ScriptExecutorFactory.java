package jasper.component;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PreDestroy;
import jasper.service.dto.TemplateDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics.monitor;
import static jasper.config.BulkheadConfiguration.updateBulkheadConfig;
import static jasper.domain.proj.Tag.localTag;
import static jasper.domain.proj.Tag.tagOrigin;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.CompletableFuture.runAsync;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class ScriptExecutorFactory {
	private static final Logger logger = LoggerFactory.getLogger(ScriptExecutorFactory.class);

	@Autowired
	MeterRegistry meterRegistry;

	@Autowired
	BulkheadRegistry registry;

	@Autowired
	Bulkhead scriptBulkhead;

	@Autowired
	ConfigCache configs;

	@Autowired
	Tagger tagger;

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<TemplateDto> message) {
		var template = message.getPayload();
		if (isBlank(template.getTag())) return;
		if (template.getTag() != null && template.getTag().startsWith("_config/security")) {
			logger.debug("Server config template updated, updating bulkhead configurations");
			bulkheads.forEach((qtag, bulkhead) -> updateBulkheadConfig(bulkhead, configs.security(tagOrigin(qtag)).scriptLimit(localTag(qtag), tagOrigin(qtag))));
		}
	}

	public CompletableFuture<Void> run(String tag, String origin, String url, Runnable runnable) throws BulkheadFullException {
		try {
			return runAsync(() -> {
				scriptBulkhead.executeRunnable(runnable);
			}, get(tag, origin)).exceptionally(e -> {
				logger.warn("{} Rate limited {} ", origin, tag);
				tagger.attachLogs(url, origin, "Rate Limit Hit " + tag);
				return null;
			});
		} catch (BulkheadFullException e) {
			logger.warn("{} Rate limited {} ", origin, tag);
			tagger.attachLogs(url, origin, "Rate Limit Hit " + tag);
			return null;
		}
	}

	private final Map<String, ExecutorService> executors = new ConcurrentHashMap<>();
	private ExecutorService get(String tag, String origin) throws BulkheadFullException {
		return bulkhead(tag, origin).executeSupplier(() -> {
			return executors.computeIfAbsent(tag + origin, k -> {
				return monitor(meterRegistry, Executors.newVirtualThreadPerTaskExecutor(), "scriptExecutor", "script", Tags.of(
					"tag", tag,
					"origin", origin));
			});
		});
	}

	private final Map<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();
	private Bulkhead bulkhead(String tag, String origin) {
		return bulkheads.computeIfAbsent(tag + origin, k -> {
			return registry.bulkhead(k, BulkheadConfig.custom()
				.maxConcurrentCalls(configs.security(origin).scriptLimit(tag, origin))
				.maxWaitDuration(ofSeconds(60))
				.build());
		});
	}

	@PreDestroy
	public void cleanup() {
		logger.info("Shutting down {} dynamic script executors and their metrics", executors.size());
		executors.values().forEach(executor -> {
			try {
				executor.shutdown();
			} catch (Exception e) {
				logger.warn("Error shutting down script executor", e);
			}
		});
		executors.clear();
	}
}
