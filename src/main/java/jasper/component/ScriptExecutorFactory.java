package jasper.component;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

import static io.micrometer.core.instrument.Timer.start;
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
	ExecutorService taskExecutor;

	@Autowired
	MeterRegistry meterRegistry;

	@Autowired
	BulkheadRegistry bulkheadRegistry;

	@Autowired
	ConfigCache configs;

	@Autowired
	Tagger tagger;

	private record ScriptResources(Timer timer, Bulkhead bulkhead) { }

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<TemplateDto> message) {
		var template = message.getPayload();
		if (isBlank(template.getTag())) return;
		if (template.getTag().startsWith("_config/security")) {
			logger.debug("Server config template updated, updating bulkhead configurations");
			resources.forEach((qtag, res) -> updateBulkheadConfig(res.bulkhead(), configs.security(tagOrigin(qtag)).scriptLimit(localTag(qtag), tagOrigin(qtag))));
		}
	}

	public CompletableFuture<Void> run(String tag, String origin, Runnable runnable) {
		return run(tag, origin, "tag:/" + tag, runnable);
	}

	public CompletableFuture<Void> run(String tag, String origin, String url, Runnable runnable) {
		var res = getResources(tag, origin);
		try {
			return runAsync(() -> res.bulkhead().executeRunnable(() -> {
				var sample = start(meterRegistry);
				try {
					runnable.run();
				} finally {
					sample.stop(res.timer());
				}
			}), taskExecutor);
		} catch (BulkheadFullException e) {
			var config = res.bulkhead().getBulkheadConfig();
			logger.warn("{} Rate limited {} (max {} for {})", origin, tag, config.getMaxConcurrentCalls(), config.getMaxWaitDuration());
			tagger.attachLogs(url, origin, "Rate Limit Hit " + tag, "Max: " + config.getMaxConcurrentCalls() + "\nWait: " + config.getMaxWaitDuration());
			return null;
		}
	}

	private final Map<String, ScriptResources> resources = new ConcurrentHashMap<>();

	private ScriptResources getResources(String tag, String origin) {
		return resources.computeIfAbsent(tag + origin, k -> new ScriptResources(
			Timer.builder("script.executor.task.duration")
				.description("Duration of script executor tasks")
				.tag("name", tag + origin)
				.tag("tag", tag)
				.tag("origin", origin)
				.register(meterRegistry),
			bulkheadRegistry.bulkhead(k, BulkheadConfig.custom()
				.maxConcurrentCalls(configs.security(origin).scriptLimit(tag, origin))
				.maxWaitDuration(ofSeconds(60))
				.build())
		));
	}
}
