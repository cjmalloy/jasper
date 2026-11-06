package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.domain.Plugin;
import jasper.errors.InvalidQueryException;
import jasper.repository.PluginRepository;
import jasper.repository.filter.TagFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WithMockUser(value = "admin", roles = "ADMIN")
@IntegrationTest
public class PluginServiceIT {

	@Autowired
	PluginService pluginService;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	ObjectMapper objectMapper;

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

	@Test
	void testCreateAndRetrievePluginWithArrayDefaults() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setDefaults(objectMapper.readTree("[\"x\", \"y\", \"z\"]"));

		pluginService.create(plugin);

		var fetched = pluginRepository.findOneByQualifiedTag("plugin/test").get();
		assertThat(fetched.getDefaults()).isNotNull();
		assertThat(fetched.getDefaults().isArray()).isTrue();
		assertThat(fetched.getDefaults()).hasSize(3);
		assertThat(fetched.getDefaults().get(0).asText()).isEqualTo("x");
		assertThat(fetched.getDefaults().get(1).asText()).isEqualTo("y");
		assertThat(fetched.getDefaults().get(2).asText()).isEqualTo("z");

		var dto = pluginService.get("plugin/test");
		assertThat(dto.getDefaults()).isNotNull();
		assertThat(dto.getDefaults().isArray()).isTrue();
		assertThat(dto.getDefaults().get(0).asText()).isEqualTo("x");
	}

	@Test
	void testCreateAndRetrievePluginWithScalarDefaults() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setDefaults(objectMapper.readTree("\"scalar-default\""));

		pluginService.create(plugin);

		var fetched = pluginRepository.findOneByQualifiedTag("plugin/test").get();
		assertThat(fetched.getDefaults()).isNotNull();
		assertThat(fetched.getDefaults().isTextual()).isTrue();
		assertThat(fetched.getDefaults().asText()).isEqualTo("scalar-default");

		var dto = pluginService.get("plugin/test");
		assertThat(dto.getDefaults()).isNotNull();
		assertThat(dto.getDefaults().isTextual()).isTrue();
		assertThat(dto.getDefaults().asText()).isEqualTo("scalar-default");
	}

	Plugin withConfig(String tag, String config, String defaults, String schema) throws Exception {
		var mapper = new ObjectMapper();
		var t = new Plugin();
		t.setTag(tag);
		if (config != null) t.setConfig((ObjectNode) mapper.readTree(config));
		if (defaults != null) t.setDefaults((ObjectNode) mapper.readTree(defaults));
		if (schema != null) t.setSchema((ObjectNode) mapper.readTree(schema));
		return pluginRepository.save(t);
	}

	List<String> configFiltered(TagFilter filter) {
		return pluginService.page(filter, PageRequest.of(0, 10, Sort.by("tag"))).getContent().stream().map(p -> p.getTag()).toList();
	}

	@Test
	void testConfigValueFilter() throws Exception {
		withConfig("plugin/a", "{\"version\": 4}", "{\"size\": 1}", "{\"properties\": {\"x\": {\"type\": \"string\"}}}");
		withConfig("plugin/b", "{\"version\": 2}", "{\"size\": 5}", null);
		withConfig("plugin/c", "{\"version\": \"x\"}", null, null);

		assertThat(configFiltered(TagFilter.builder().filterAfter(List.of("config->version:num/3")).build()))
			.containsExactly("plugin/a");
		assertThat(configFiltered(TagFilter.builder().filterBefore(List.of("defaults->size:num/3")).build()))
			.containsExactly("plugin/a");
		assertThat(configFiltered(TagFilter.builder().filterAfter(List.of("schema->properties->x->type/r")).build()))
			.containsExactly("plugin/a");
		assertThat(configFiltered(TagFilter.builder()
				.filterAfter(List.of("config->version:num/1"))
				.filterBefore(List.of("defaults->size:num/3")).build()))
			.containsExactly("plugin/a");
	}

	@Test
	void testInvalidConfigValueFilter() {
		assertThatThrownBy(() -> pluginService.page(TagFilter.builder().filterAfter(List.of("external->ids:len/0")).build(), PageRequest.of(0, 10)))
			.isInstanceOf(InvalidQueryException.class);
		assertThatThrownBy(() -> pluginService.page(TagFilter.builder().filterAfter(List.of("config->version:num/abc")).build(), PageRequest.of(0, 10)))
			.isInstanceOf(InvalidQueryException.class);
	}
}
