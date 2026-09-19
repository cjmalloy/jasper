package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.domain.Metadata;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.errors.InvalidPluginException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
public class ValidateRefIT {

	@Autowired
	Validate validate;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	static final String URL = "https://www.example.com/";

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		pluginRepository.deleteAll();
	}

	@Test
	void testValidateRef() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester"));
		refRepository.save(ref);

		validate.ref("", ref, false);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(booleans = {false, true})
	void testSourcePublishedDateAutofix(Boolean obsolete) {
		var source = new Ref();
		source.setUrl(URL + "source");
		source.setOrigin("@a.archive");
		source.setPublished(Instant.parse("2024-01-01T12:00:00Z"));
		if (obsolete != null) source.setMetadata(Metadata.builder().obsolete(obsolete).build());
		refRepository.saveAndFlush(source);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@a");
		ref.setSources(List.of(source.getUrl()));
		var published = Instant.parse("2020-01-01T00:00:00Z");
		ref.setPublished(published);

		validate.ref("@a", ref);

		assertThat(ref.getPublished()).isEqualTo(Boolean.TRUE.equals(obsolete)
			? published : source.getPublished().plusMillis(1));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(booleans = {false, true})
	void testResponsePublishedDateAutofix(Boolean obsolete) {
		var response = new Ref();
		response.setUrl(URL + "response");
		response.setOrigin("@a.archive");
		response.setSources(List.of(URL));
		response.setPublished(Instant.parse("2020-01-01T00:00:00Z"));
		if (obsolete != null) response.setMetadata(Metadata.builder().obsolete(obsolete).build());
		refRepository.saveAndFlush(response);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@a");
		var published = Instant.parse("2024-01-01T12:00:00Z");
		ref.setPublished(published);

		validate.ref("@a", ref);

		assertThat(ref.getPublished()).isEqualTo(Boolean.TRUE.equals(obsolete)
			? published : response.getPublished().minusMillis(1));
	}

	@ParameterizedTest
	@CsvSource({
		"0, 0",
		"0, 1",
		"1, 0",
		"1, 1",
		"86400000, 86400000"
	})
	void testValidPublishedDatesPreserved(long sourceAgeMillis, long responseDelayMillis) {
		var published = Instant.parse("2024-01-01T12:00:00.123456Z");
		var sourcePublished = published.minusMillis(sourceAgeMillis);
		var responsePublished = published.plusMillis(responseDelayMillis);
		var source = new Ref();
		source.setUrl(URL + "source");
		source.setPublished(sourcePublished);
		source = refRepository.saveAndFlush(source);
		var response = new Ref();
		response.setUrl(URL + "response");
		response.setSources(List.of(URL));
		response.setPublished(responsePublished);
		response = refRepository.saveAndFlush(response);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setSources(List.of(source.getUrl()));
		ref.setPublished(published);

		for (var stripOnError : List.of(false, true)) {
			validate.ref("", ref, stripOnError);

			assertThat(ref.getPublished()).isEqualTo(published);
			assertThat(source.getPublished()).isEqualTo(sourcePublished);
			assertThat(response.getPublished()).isEqualTo(responsePublished);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"1900-01-01T00:00:00Z",
		"1970-01-01T00:00:00Z",
		"2024-02-29T23:59:59.123456789Z",
		"2100-01-01T00:00:00Z"
	})
	void testUnconstrainedPublishedDatesPreserved(Instant published) {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setPublished(published);

		validate.ref("", ref);

		assertThat(ref.getPublished()).isEqualTo(published);
		ref.setSources(List.of());

		validate.ref("", ref);

		assertThat(ref.getPublished()).isEqualTo(published);
		ref.setSources(List.of(URL + "missing"));

		validate.ref("", ref);

		assertThat(ref.getPublished()).isEqualTo(published);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "@a"})
	void testValidPublishedDateWithMultipleRelatedRefsPreserved(String rootOrigin) {
		var published = Instant.parse("2024-01-01T12:00:00Z");
		var source = new Ref();
		source.setUrl(URL + "source");
		source.setOrigin("@a");
		source.setPublished(published.minusSeconds(60));
		var archivedSource = new Ref();
		archivedSource.setUrl(source.getUrl());
		archivedSource.setOrigin("@a.archive");
		archivedSource.setPublished(published);
		var otherSource = new Ref();
		otherSource.setUrl(URL + "other-source");
		otherSource.setOrigin("@a");
		otherSource.setPublished(published.minusMillis(1));
		var response = new Ref();
		response.setUrl(URL + "response");
		response.setOrigin("@a");
		response.setSources(List.of(URL));
		response.setPublished(published.plusMillis(1));
		var userResponse = new Ref();
		userResponse.setUrl(URL + "user-response");
		userResponse.setOrigin("@a.archive");
		userResponse.setSources(List.of(URL));
		userResponse.setTags(List.of("plugin/user"));
		userResponse.setPublished(published);
		var obsoleteSource = new Ref();
		obsoleteSource.setUrl(source.getUrl());
		obsoleteSource.setOrigin("@a.obsolete");
		obsoleteSource.setPublished(published.plusSeconds(60));
		obsoleteSource.setMetadata(Metadata.builder().obsolete(true).build());
		var obsoleteResponse = new Ref();
		obsoleteResponse.setUrl(URL + "obsolete-response");
		obsoleteResponse.setOrigin("@a.archive");
		obsoleteResponse.setSources(List.of(URL));
		obsoleteResponse.setPublished(published.minusSeconds(60));
		obsoleteResponse.setMetadata(Metadata.builder().obsolete(true).build());
		var related = refRepository.saveAllAndFlush(List.of(
			source, archivedSource, otherSource, response, userResponse, obsoleteSource, obsoleteResponse));
		var relatedDates = related.stream().map(Ref::getPublished).toList();
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@a");
		ref.setSources(List.of(source.getUrl(), otherSource.getUrl(), URL + "missing"));
		ref.setPublished(published);

		for (var stripOnError : List.of(false, true)) {
			validate.ref(rootOrigin, ref, stripOnError);

			assertThat(ref.getPublished()).isEqualTo(published);
			assertThat(related).extracting(Ref::getPublished).containsExactlyElementsOf(relatedDates);
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {-1, 0, 1})
	void testSelfReferencePublishedDatePreserved(long offsetMillis) {
		var published = Instant.parse("2024-01-01T12:00:00Z");
		var archived = new Ref();
		archived.setUrl(URL);
		archived.setOrigin("@a.archive");
		archived.setSources(List.of(URL));
		archived.setPublished(published.plusMillis(offsetMillis));
		archived = refRepository.saveAndFlush(archived);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@a");
		ref.setSources(List.of(URL));
		ref.setPublished(published);

		validate.ref("@a", ref);

		assertThat(ref.getPublished()).isEqualTo(published);
		assertThat(archived.getPublished()).isEqualTo(published.plusMillis(offsetMillis));
	}

	@ParameterizedTest
	@ValueSource(longs = {-1, 0, 1})
	void testUserResponseDoesNotChangeValidPublishedDate(long offsetMillis) {
		var published = Instant.parse("2024-01-01T12:00:00Z");
		var response = new Ref();
		response.setUrl(URL + "user-response");
		response.setSources(List.of(URL));
		response.setTags(List.of("plugin/user"));
		response.setPublished(published.plusMillis(offsetMillis));
		response = refRepository.saveAndFlush(response);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setPublished(published);

		for (var stripOnError : List.of(false, true)) {
			validate.ref("", ref, stripOnError);

			assertThat(ref.getPublished()).isEqualTo(published);
			assertThat(response.getPublished()).isEqualTo(published.plusMillis(Math.max(0, offsetMillis)));
		}
	}

	@Test
	void testPublishedDatesOutsideRootOriginIgnored() {
		var source = new Ref();
		source.setUrl(URL + "source");
		source.setOrigin("@b");
		source.setPublished(Instant.parse("2025-01-01T00:00:00Z"));
		refRepository.saveAndFlush(source);
		var response = new Ref();
		response.setUrl(URL + "response");
		response.setOrigin("@b");
		response.setSources(List.of(URL));
		response.setPublished(Instant.parse("2020-01-01T00:00:00Z"));
		refRepository.saveAndFlush(response);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@a");
		ref.setSources(List.of(source.getUrl()));
		var published = Instant.parse("2024-01-01T12:00:00Z");
		ref.setPublished(published);

		validate.ref("@a", ref);

		assertThat(ref.getPublished()).isEqualTo(published);
	}

	@Test
	void testValidateRefWithInvalidPlugin() throws IOException {
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
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));

		assertThatThrownBy(() -> validate.ref("", ref, false))
			.isInstanceOf(InvalidPluginException.class);
	}

	@Test
	void testValidateRefWithInvalidPluginDefaults() throws IOException {
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
		plugin.setDefaults((ObjectNode) mapper.readTree("""
		{
			"invalid": "defaults"
		}"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));

		assertThatThrownBy(() -> validate.ref("", ref, false))
			.isInstanceOf(InvalidPluginException.class);
	}

	@Test
	void testValidateRefWithPlugin() throws IOException {
		var mapper = new ObjectMapper();
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(Arrays.asList("+user/tester", "plugin/test")));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": {
				"name": "Alice",
				"age": 100
			}
		}"""));

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithOptionalPlugin() throws IOException {
		var mapper = new ObjectMapper();
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		plugin.setSchema((ObjectNode) mapper.readTree("""
		{
			"optionalProperties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(new ArrayList<>(Arrays.asList("+user/tester", "plugin/test")));

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithStringPlugin() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		var mapper = new ObjectMapper();
		plugin.setSchema((ObjectNode) mapper.readTree("""
			{ "type": "string" },
		"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": "test"
		}"""));

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithBooleanPlugin() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		var mapper = new ObjectMapper();
		plugin.setSchema((ObjectNode) mapper.readTree("""
			{ "type": "boolean" },
		"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": true
		}"""));

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithNumberPlugin() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		var mapper = new ObjectMapper();
		plugin.setSchema((ObjectNode) mapper.readTree("""
			{ "type": "uint32" },
		"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": 100
		}"""));

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithArrayPlugin() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		var mapper = new ObjectMapper();
		plugin.setSchema((ObjectNode) mapper.readTree("""
			{ "elements": { "type": "string" } },
		"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": ["test", "works"]
		}"""));

		validate.ref("", ref, false);
	}

	@Test
	void testValidateRefWithPluginExtraFailed() throws IOException {
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
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": {
				"name": "Alice",
				"age": 100
			},
			"extraStuff": {
				"is": "not allowed"
			}
		}"""));

		assertThatThrownBy(() -> validate.ref("", ref, false))
			.isInstanceOf(InvalidPluginException.class);
	}

	@Test
	void testValidateRefWithSchemalessPluginFailed() throws IOException {
		var mapper = new ObjectMapper();
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": {
				"name": "Alice",
				"age": 100
			}
		}"""));

		assertThatThrownBy(() -> validate.ref("", ref, false))
			.isInstanceOf(InvalidPluginException.class);
	}

	@Test
	void testValidateRefWithPluginDefaults() throws IOException {
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		var mapper = new ObjectMapper();
		plugin.setDefaults((ObjectNode) mapper.readTree("""
		{
			"name": "Alice",
			"age": 100
		}"""));
		plugin.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" },
				"age": { "type": "uint32" }
			}
		}"""));
		pluginRepository.save(plugin);
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(Arrays.asList("+user/tester", "plugin/test"));

		validate.ref("", ref, false);
	}

	@Test
	void testResponseValidationWithNoRoles() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Response Ref");
		ref.setTags(new ArrayList<>(Arrays.asList("+user/tester", "seal", "+seal", "_seal", "_moderated", "plugin/qc")));

		validate.response("", ref);

		// Without any roles, all mod and editor seals should be removed
		assertThat(ref.getTags())
			.contains("+user/tester")
			.doesNotContain("seal", "+seal", "_seal", "_moderated", "plugin/qc");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testResponseValidationWithUserRole() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Response Ref");
		ref.setTags(new ArrayList<>(Arrays.asList("+user/tester", "seal", "+seal", "_seal", "_moderated", "plugin/qc")));

		validate.response("", ref);

		// With USER role (not MOD or EDITOR), all mod and editor seals should be removed
		assertThat(ref.getTags())
			.contains("+user/tester")
			.doesNotContain("seal", "+seal", "_seal", "_moderated", "plugin/qc");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"MOD"})
	void testResponseValidationWithModRole() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Response Ref");
		ref.setTags(new ArrayList<>(Arrays.asList("+user/tester", "seal", "+seal", "_seal", "_moderated", "plugin/qc")));

		validate.response("", ref);

		// With MOD role, due to role hierarchy MOD > EDITOR, both mod and editor seals are kept
		assertThat(ref.getTags())
			.contains("+user/tester", "seal", "+seal", "_seal", "_moderated", "plugin/qc");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"EDITOR"})
	void testResponseValidationWithEditorRole() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Response Ref");
		ref.setTags(new ArrayList<>(Arrays.asList("+user/tester", "seal", "+seal", "_seal", "_moderated", "plugin/qc")));

		validate.response("", ref);

		// With EDITOR role (but not MOD), editor seals are kept but mod seals are removed
		assertThat(ref.getTags())
			.contains("+user/tester", "plugin/qc")
			.doesNotContain("seal", "+seal", "_seal", "_moderated");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"MOD", "EDITOR"})
	void testResponseValidationWithBothModAndEditorRoles() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Response Ref");
		ref.setTags(new ArrayList<>(Arrays.asList("+user/tester", "seal", "+seal", "_seal", "_moderated", "plugin/qc")));

		validate.response("", ref);

		// With both MOD and EDITOR roles, all seals should be kept
		assertThat(ref.getTags())
			.contains("+user/tester", "seal", "+seal", "_seal", "_moderated", "plugin/qc");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testResponseValidationCallsPluginValidation() throws IOException {
		// Create a plugin with schema
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		var mapper = new ObjectMapper();
		plugin.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" }
			}
		}"""));
		pluginRepository.save(plugin);

		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Response Ref");
		ref.setTags(new ArrayList<>(Arrays.asList("+user/tester", "plugin/test")));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": {
				"name": "Alice"
			}
		}"""));

		// Should pass validation
		validate.response("", ref);

		assertThat(ref.getTags())
			.contains("+user/tester", "plugin/test");
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"USER"})
	void testResponseValidationFailsWithInvalidPlugin() throws IOException {
		// Create a plugin with schema
		var plugin = new Plugin();
		plugin.setTag("plugin/test");
		var mapper = new ObjectMapper();
		plugin.setSchema((ObjectNode) mapper.readTree("""
		{
			"properties": {
				"name": { "type": "string" }
			}
		}"""));
		pluginRepository.save(plugin);

		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("Response Ref");
		ref.setTags(new ArrayList<>(Arrays.asList("+user/tester", "plugin/test")));
		ref.setPlugins((ObjectNode) mapper.readTree("""
		{
			"plugin/test": {
				"name": 123
			}
		}"""));

		// Should fail validation because name should be string, not number
		assertThatThrownBy(() -> validate.response("", ref))
			.isInstanceOf(InvalidPluginException.class);
	}
}
