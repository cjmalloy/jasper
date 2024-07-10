package jasper.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jasper.component.dto.Bundle;
import jasper.component.dto.ComponentDtoMapper;
import jasper.component.vm.JavaScript;
import jasper.domain.Ref;
import jasper.errors.ScriptException;
import jasper.plugin.config.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Component
public class ScriptRunner {
	private static final Logger logger = LoggerFactory.getLogger(ScriptRunner.class);

	@Autowired
	IngestBundle ingest;

	@Autowired
	Tagger tagger;

	@Autowired
	JavaScript vm;

	@Autowired
	ComponentDtoMapper mapper;

	@Autowired
	ObjectMapper objectMapper;

	@Timed("jasper.scripts")
	public void runScripts(Ref ref, Script config) {
		if (isBlank(config.getScript())) return;
		if (!"javascript".equals(config.getLanguage())) {
			// Only Javascript is supported right now
			logger.error("{} Script runtime not supported {}", ref.getOrigin(), config.getLanguage());
			tagger.attachError(ref.getOrigin(), ref, "Script runtime not supported: " + config.getLanguage());
			return;
		}
		String output;
		try {
			output = vm.runJavaScript(config.getScript(), objectMapper.writeValueAsString(mapper.domainToDto(ref)), config.getTimeoutMs());
		} catch (ScriptException e) {
			logger.error("{} Error running script", ref.getOrigin(), e);
			tagger.attachError(ref.getUrl(), ref.getOrigin(), e.getMessage(), e.getLogs());
			return;
		} catch (Exception e) {
			logger.error("{} Error running script", ref.getOrigin(), e);
			tagger.attachError(ref.getUrl(), ref.getOrigin(), e.getMessage());
			return;
		}
		try {
			var bundle = objectMapper.readValue(output, new TypeReference<Bundle>() {});
			ingest.createOrUpdate(bundle, ref.getOrigin());
		} catch (Exception e) {
			logger.error("{} Error parsing script return value", ref.getOrigin(), e);
			tagger.attachError(ref.getUrl(), ref.getOrigin(), "Error parsing script output", output);
		}
	}

}
