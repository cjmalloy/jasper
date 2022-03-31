package ca.hc.jasper.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import ca.hc.jasper.IntegrationTest;
import ca.hc.jasper.domain.Plugin;
import ca.hc.jasper.repository.PluginRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@WithMockUser(value = "admin", roles = "ADMIN")
@IntegrationTest
@Transactional
public class PluginServiceIT {

	@Autowired
	PluginService pluginService;

	@Autowired
	PluginRepository pluginRepository;

	@Test
	void testCreateUserTagWithSchema() throws IOException {
		var tag = new Plugin();
		tag.setTag("plugin/test");
		var mapper = new ObjectMapper();
		tag.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));

		pluginService.create(tag);

		assertThat(pluginRepository.existsByQualifiedTag("plugin/test"))
			.isTrue();
		var fetched = pluginRepository.findOneByQualifiedTag("plugin/test").get();
		assertThat(fetched.getTag())
			.isEqualTo("plugin/test");
	}
}
