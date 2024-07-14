package jasper.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jasper.component.dto.Bundle;
import jasper.component.dto.ComponentDtoMapper;
import jasper.component.vm.JavaScript;
import jasper.component.vm.Python;
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
	JavaScript jsVm;

	@Autowired
	Python pythonVm;

	@Autowired
	ComponentDtoMapper mapper;

	@Autowired
	ObjectMapper objectMapper;

	@Timed("jasper.scripts")
	public void runScripts(Ref ref, Script config) {
		if (isBlank(config.getScript())) return;
		// TODO: script hashing to pre-approve scripts
		String output;
		try {
			switch (config.getLanguage()) {
			case "javascript":
				output = jsVm.runJavaScript(config.getScript(), objectMapper.writeValueAsString(mapper.domainToDto(ref)), config.getTimeoutMs());
				break;
			case "python":
				output = pythonVm.runPython(config.getScript(), objectMapper.writeValueAsString(mapper.domainToDto(ref)), config.getTimeoutMs());
				break;
			default:
				logger.error("{} Script runtime not supported {}", ref.getOrigin(), config.getLanguage());
				tagger.attachError(ref.getOrigin(), ref, "Script runtime not supported: " + config.getLanguage());
				return;
			}
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
