package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.domain.Plugin;
import jasper.repository.PluginRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@WithMockUser(value = "admin", roles = "ADMIN")
@IntegrationTest
public class PluginServiceIT {

	@Autowired
	PluginService pluginService;

	@Autowired
	PluginRepository pluginRepository;

	@BeforeEach
	void init() {
		pluginRepository.deleteAll();
	}

	@Test
	void testCreatePluginWithSchema() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		var mapper = new ObjectMapper();
		plugin.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));

		pluginService.create(plugin);

		assertThat(pluginRepository.existsByQualifiedTag("plugin/test"))
			.isTrue();
		var fetched = pluginRepository.findOneByQualifiedTag("plugin/test").get();
		assertThat(fetched.getTag())
			.isEqualTo("plugin/test");
	}
}
