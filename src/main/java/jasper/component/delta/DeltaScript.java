package jasper.component.delta;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import jakarta.annotation.PostConstruct;
import jasper.component.ConfigCache;
import jasper.component.ScriptRunner;
import jasper.component.Tagger;
import jasper.domain.Ref;
import jasper.errors.UntrustedScriptException;
import jasper.plugin.config.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static jasper.domain.proj.Tag.matchesTag;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Profile("scripts")
@Component
public class DeltaScript implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(DeltaScript.class);

	@Autowired
	ConfigCache configs;

	@Autowired
	Async async;

	@Autowired
	ScriptRunner scriptRunner;

	@Autowired
	Tagger tagger;

	// Map to store per-origin bulkheads for script execution
	private final Map<String, Bulkhead> originScriptBulkheads = new ConcurrentHashMap<>();

	private Bulkhead getOriginScriptBulkhead(String origin) {
		return originScriptBulkheads.computeIfAbsent(origin, k -> {
			var maxConcurrent = configs.root().getMaxConcurrentScriptsPerOrigin();
			logger.debug("Creating script execution bulkhead for origin {} with {} permits", origin, maxConcurrent);
			
			var bulkheadConfig = BulkheadConfig.custom()
				.maxConcurrentCalls(maxConcurrent)
				.maxWaitDuration(java.time.Duration.ofMillis(0)) // Don't wait, fail fast
				.build();
			
			return Bulkhead.of("script-" + origin, bulkheadConfig);
		});
	}

	@PostConstruct
	void init() {
		async.addAsyncTag("plugin/delta", this);
		async.addAsyncTag("_plugin/delta", this);
	}

	@Override
	public String signature() {
		return "+plugin/delta";
	}

	@Override
	public void run(Ref ref) throws Exception {
		if (ref.hasTag("_seal/delta")) return;
		if (ref.hasTag("_plugin/delta/scrape")) return; // TODO: Move to mod scripts
		if (ref.hasTag("_plugin/delta/cache")) return; // TODO: Move to mod scripts
		
		var bulkhead = getOriginScriptBulkhead(ref.getOrigin());
		
		// Try to execute within bulkhead limits
		try {
			bulkhead.executeSupplier(() -> {
				try {
					logger.debug("{} Searching for delta response scripts for {} ({})", ref.getOrigin(), ref.getTitle(), ref.getUrl());
					var found = false;
					var tags = ref.getExpandedTags().stream()
						.filter(t -> matchesTag("plugin/delta", t) || matchesTag("_plugin/delta", t))
						.sorted()
						.toList()
						.reversed();
					for (var scriptTag : tags) {
						var config = configs.getPluginConfig(scriptTag, ref.getOrigin(), Script.class);
						if (config.isPresent() && isNotBlank(config.get().getScript())) {
							try {
								logger.info("{} Applying delta response {} to {} ({})", ref.getOrigin(), scriptTag, ref.getTitle(), ref.getUrl());
								scriptRunner.runScripts(ref, scriptTag, config.get());
							} catch (UntrustedScriptException e) {
								logger.error("{} Script hash not whitelisted: {}", ref.getOrigin(), e.getScriptHash());
								tagger.attachError(ref.getOrigin(), ref, "Script hash not whitelisted", e.getScriptHash());
							}
							found = true;
						}
					}
					if (!found) tagger.attachError(ref.getOrigin(), ref, "Could not find delta script", String.join(", ", ref.getTags()));
					return null;
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		} catch (io.github.resilience4j.bulkhead.BulkheadFullException e) {
			logger.warn("{} Script execution rate limit exceeded for {} ({})", ref.getOrigin(), ref.getTitle(), ref.getUrl());
			tagger.attachError(ref.getOrigin(), ref, "Script execution rate limit exceeded", "Too many concurrent scripts running");
		}
	}

}
