package jasper.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.domain.Plugin;
import jasper.domain.Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class DisabledConfigRepositoryIT {

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	TemplateRepository templateRepository;

	@BeforeEach
	void init() {
		pluginRepository.deleteAllInBatch();
		templateRepository.deleteAllInBatch();
	}

	ObjectNode json(String json) {
		try {
			return (ObjectNode) new ObjectMapper().readTree(json);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	void plugin(String tag, String config) {
		var plugin = new Plugin();
		plugin.setTag(tag);
		plugin.setOrigin("");
		if (config != null) plugin.setConfig(json(config));
		pluginRepository.save(plugin);
	}

	void template(String tag, String config) {
		var template = new Template();
		template.setTag(tag);
		template.setOrigin("");
		if (config != null) template.setConfig(json(config));
		template.setSchema(json("{\"properties\": {\"name\": {\"type\": \"string\"}}}"));
		template.setDefaults(json("{\"name\": \"test\"}"));
		templateRepository.save(template);
	}

	@Test
	void testDisabledPlugin() {
		plugin("plugin/enabled", null);
		plugin("plugin/notdisabled", "{\"disabled\": false}");
		plugin("plugin/disabled", "{\"disabled\": true}");

		assertThat(pluginRepository.findByTagAndOrigin("plugin/enabled", "")).isPresent();
		assertThat(pluginRepository.findByTagAndOrigin("plugin/notdisabled", "")).isPresent();
		assertThat(pluginRepository.findByTagAndOrigin("plugin/disabled", "")).isEmpty();
	}

	@Test
	void testDisabledTemplate() {
		template("enabled", null);
		template("notdisabled", "{\"disabled\": false}");
		template("disabled", "{\"disabled\": true}");

		assertThat(templateRepository.findByTemplateAndOrigin("enabled", "")).isPresent();
		assertThat(templateRepository.findByTemplateAndOrigin("notdisabled", "")).isPresent();
		assertThat(templateRepository.findByTemplateAndOrigin("disabled", "")).isEmpty();
		assertThat(templateRepository.findAllForTagAndOriginWithSchema("disabled", "")).isEmpty();
		assertThat(templateRepository.findAllForTagAndOriginWithDefaults("disabled", "")).isEmpty();
		assertThat(templateRepository.findAllForTagAndOriginWithSchema("notdisabled", ""))
			.extracting(Template::getTag).containsExactly("notdisabled");
	}
}
