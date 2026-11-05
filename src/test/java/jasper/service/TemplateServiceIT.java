package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.domain.Template;
import jasper.errors.InvalidQueryException;
import jasper.repository.TemplateRepository;
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
public class TemplateServiceIT {

	@Autowired
	TemplateService templateService;

	@Autowired
	TemplateRepository templateRepository;

	@BeforeEach
	void init() {
		templateRepository.deleteAll();
	}

	@Test
	void testCreateTemplateWithSchema() throws IOException {
		var template = new Template();
		template.setTag("test");
		var mapper = new ObjectMapper();
		template.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));

		templateService.create(template);

		assertThat(templateRepository.existsByQualifiedTag("test"))
			.isTrue();
		var fetched = templateRepository.findOneByQualifiedTag("test").get();
		assertThat(fetched.getTag())
			.isEqualTo("test");
	}

	Template template(String tag) {
		var t = new Template();
		t.setTag(tag);
		templateRepository.save(t);
		return t;
	}

	@Test
	void testGetPageRefWithQuery() {
		template("public");
		template("custom");
		template("extra");

		var page = templateService.page(
			TagFilter
				.builder()
				.query("custom")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	@Test
	void testGetEmptyPageRefWithEmptyQuery() {
		template("public");
		template("custom");
		template("extra");

		var page = templateService.page(
			TagFilter
				.builder()
				.query("!@*")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testGetPageRefWithEmptyQueryRoot() {
		template("");
		template("custom");
		template("extra");

		var page = templateService.page(
			TagFilter
				.builder()
				.query("@*")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(3);
	}

	@Test
	void testGetEmptyPageRefWithEmptyQueryRoot() {
		template("");
		template("custom");
		template("extra");

		var page = templateService.page(
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
		template("test");

		var page = templateService.page(
			TagFilter
				.builder()
				.query("!test")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(0);
	}

	@Test
	void testGetPageWithNotQueryFoundRef() {
		template("public");

		var page = templateService.page(
			TagFilter
				.builder()
				.query("!test")
				.build(),
			PageRequest.of(0, 10));

		assertThat(page.getTotalElements())
			.isEqualTo(1);
	}

	Template withConfig(String tag, String config, String defaults, String schema) throws Exception {
		var mapper = new ObjectMapper();
		var t = new Template();
		t.setTag(tag);
		if (config != null) t.setConfig((ObjectNode) mapper.readTree(config));
		if (defaults != null) t.setDefaults((ObjectNode) mapper.readTree(defaults));
		if (schema != null) t.setSchema((ObjectNode) mapper.readTree(schema));
		return templateRepository.save(t);
	}

	List<String> configFiltered(TagFilter filter) {
		return templateService.page(filter, PageRequest.of(0, 10, Sort.by("tag"))).getContent().stream().map(p -> p.getTag()).toList();
	}

	@Test
	void testConfigValueFilter() throws Exception {
		withConfig("plugin/a", "{\"version\": 4}", "{\"size\": 1}", "{\"properties\": {\"x\": {\"type\": \"string\"}}}");
		withConfig("plugin/b", "{\"version\": 2}", "{\"size\": 5}", null);
		withConfig("plugin/c", "{\"version\": \"x\"}", null, null);

		assertThat(configFiltered(TagFilter.builder().configAfter(List.of("config->version:num/3")).build()))
			.containsExactly("plugin/a");
		assertThat(configFiltered(TagFilter.builder().configBefore(List.of("defaults->size:num/3")).build()))
			.containsExactly("plugin/a");
		assertThat(configFiltered(TagFilter.builder().configAfter(List.of("schema->properties->x->type/r")).build()))
			.containsExactly("plugin/a");
		assertThat(configFiltered(TagFilter.builder()
				.configAfter(List.of("config->version:num/1"))
				.configBefore(List.of("defaults->size:num/3")).build()))
			.containsExactly("plugin/a");
	}

	@Test
	void testInvalidConfigValueFilter() {
		assertThatThrownBy(() -> templateService.page(TagFilter.builder().configAfter(List.of("external->ids:len/0")).build(), PageRequest.of(0, 10)))
			.isInstanceOf(InvalidQueryException.class);
		assertThatThrownBy(() -> templateService.page(TagFilter.builder().configAfter(List.of("config->version:num/abc")).build(), PageRequest.of(0, 10)))
			.isInstanceOf(InvalidQueryException.class);
	}
}
