package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.domain.Plugin;
import jasper.repository.PluginRepository;
import jasper.repository.filter.TagFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
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
		var fetched = pluginRepository.findFirstByQualifiedTagOrderByModifiedDesc("plugin/test").get();
		assertThat(fetched.getTag())
			.isEqualTo("plugin/test");
	}

	Plugin plugin(String tag) {
		var t = new Plugin();
		t.setTag(tag);
		pluginRepository.save(t);
		return t;
	}

	@Test
	void testGetPageRefWithQuery() {
		plugin("plugin/public");
		plugin("plugin/custom");
		plugin("plugin/extra");

		var page = pluginService.page(
			TagFilter
				.builder()
				.query("plugin/custom")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	@Test
	void testGetEmptyPageRefWithEmptyQuery() {
		plugin("plugin/custom");
		plugin("plugin/extra");

		var page = pluginService.page(
			TagFilter
				.builder()
				.query("!@*")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testGetPageWithNotQueryRef() {
		plugin("plugin/test");

		var page = pluginService.page(
			TagFilter
				.builder()
				.query("!plugin/test")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testGetPageWithNotQueryFoundRef() {
		plugin("plugin/public");

		var page = pluginService.page(
			TagFilter
				.builder()
				.query("!plugin/test")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}
}
