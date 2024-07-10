package jasper.component.delta;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.ConfigCache;
import jasper.component.IngestBundle;
import jasper.component.Tagger;
import jasper.component.Vm;
import jasper.component.dto.Bundle;
import jasper.component.dto.ComponentDtoMapper;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.errors.ScriptException;
import jasper.plugin.config.Delta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static jasper.domain.proj.Tag.matchesTag;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Profile("scripts")
@Component
public class Script implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(Script.class);

	@Autowired
	Props props;

	@Autowired
	Async async;

	@Autowired
	IngestBundle ingest;

	@Autowired
	Tagger tagger;

	@Autowired
	Vm vm;

	@Autowired
	ConfigCache configs;

	@Autowired
	ComponentDtoMapper mapper;

	@Autowired
	ObjectMapper objectMapper;

	@PostConstruct
	void init() {
		async.addAsyncTag("plugin/delta", this);
	}

	@Override
	public String signature() {
		return "+plugin/delta";
	}

	@Override
	public void run(Ref ref) throws Exception {
		logger.debug("Applying delta response to {} ({})", ref.getTitle(), ref.getUrl());
		for (var scriptTag : ref.getTags().stream().filter(t -> matchesTag("plugin/delta", t)).toList()) {
			var delta = configs.getPluginConfig(scriptTag, ref.getOrigin(), Delta.class);
			if (isBlank(delta.getScript())) continue;
			if (!"javascript".equals(delta.getLanguage())) {
				// Only Javascript is supported right now
				logger.error("Script runtime not supported {}", delta.getLanguage());
				tagger.attachError(ref.getOrigin(), ref, "Script runtime not supported: " + delta.getLanguage());
				return;
			}
			String output;
			try {
				output = vm.runJavaScript(delta.getScript(), objectMapper.writeValueAsString(mapper.domainToDto(ref)), delta.getTimeoutMs());
			} catch (ScriptException e) {
				logger.error("Error running script", e);
				tagger.attachError(ref.getUrl(), ref.getOrigin(), e.getMessage(), e.getLogs());
				return;
			} catch (Exception e) {
				logger.error("Error running script", e);
				tagger.attachError(ref.getUrl(), ref.getOrigin(), e.getMessage());
				return;
			}
			try {
				var bundle = objectMapper.readValue(output, new TypeReference<Bundle>() {});
				ingest.createOrUpdate(bundle, ref.getOrigin());
			} catch (Exception e) {
				logger.error("Error parsing script return value", e);
				tagger.attachError(ref.getUrl(), ref.getOrigin(), "Error parsing script output", output);
			}
		}
	}

}
