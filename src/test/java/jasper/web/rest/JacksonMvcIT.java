package jasper.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.domain.Ext;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.repository.ExtRepository;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Pins the HTTP JSON (de)serialization defaults of the application mapper
 * on {@link RefController} and {@link ExtController}.
 */
@WithMockUser(value = "+user/tester", roles = {"ADMIN"})
@AutoConfigureMockMvc
@IntegrationTest
class JacksonMvcIT {

	private static final String URL = "https://www.example.com/jackson";
	/**
	 * ISO-8601 as written by InstantSerializer: fraction printed in groups of 3 digits.
	 */
	private static final String ISO_INSTANT = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.(\\d{3}){1,3})?Z";
	private static final String ISO_INSTANT_MICROS = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.(\\d{3}){1,2})?Z";
	private static final String MALFORMED_DETAIL_SUFFIX = "\n at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: ";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RefRepository refRepository;

	@Autowired
	private ExtRepository extRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ConfigCache configCache;

	@Autowired
	private Environment env;

	@Autowired
	private Props props;

	private String defaultRole;

	@BeforeEach
	void setup() {
		// The JWTFilter replaces the mock user with the default user, so grant it write access to existing Refs
		defaultRole = props.getDefaultRole();
		props.setDefaultRole("ROLE_MOD");
		refRepository.deleteAll();
		extRepository.deleteAll();
		configCache.clearUserCache();
		configCache.clearPluginCache();
		configCache.clearTemplateCache();
	}

	@AfterEach
	void cleanup() {
		props.setDefaultRole(defaultRole);
	}

	private boolean sqlite() {
		return env.matchesProfiles("sqlite");
	}

	private MockHttpServletResponse postRef(String body) throws Exception {
		return mockMvc
			.perform(post("/api/v1/ref")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body.getBytes(StandardCharsets.UTF_8))
				.with(csrf().asHeader()))
			.andReturn().getResponse();
	}

	private MockHttpServletResponse putRef(String body) throws Exception {
		return mockMvc
			.perform(put("/api/v1/ref")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body.getBytes(StandardCharsets.UTF_8))
				.with(csrf().asHeader()))
			.andReturn().getResponse();
	}

	private String getRefBody(String url, String origin) throws Exception {
		var response = mockMvc
			.perform(get("/api/v1/ref").param("url", url).param("origin", origin))
			.andReturn().getResponse();
		assertThat(response.getStatus()).isEqualTo(200);
		return response.getContentAsString(StandardCharsets.UTF_8);
	}

	private JsonNode getRef() throws Exception {
		return objectMapper.readTree(getRefBody(URL, ""));
	}

	private static List<String> fieldNames(JsonNode node) {
		var result = new ArrayList<String>();
		node.fieldNames().forEachRemaining(result::add);
		return result;
	}

	private String expectedStored(String sqlite, String postgres) {
		return sqlite() ? sqlite : postgres;
	}

	// Instants

	@Test
	void testPostReturnsIsoInstantString() throws Exception {
		var response = postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"]}");

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(response.getContentType()).isEqualTo("application/json");
		var body = response.getContentAsString();
		assertThat(body).matches("\"" + ISO_INSTANT + "\"");
		var cursor = objectMapper.readValue(body, Instant.class);
		assertThat(body).isEqualTo("\"" + cursor + "\"");
	}

	@Test
	void testGetRefInstantFormat() throws Exception {
		assertThat(postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"]}").getStatus()).isEqualTo(201);

		var ref = getRef();
		for (var field : List.of("published", "created", "modified")) {
			assertThat(ref.get(field).isTextual()).as(field).isTrue();
			// SQLite keeps nanoseconds, PostgreSQL timestamps are microseconds
			assertThat(ref.get(field).asText()).as(field).matches(sqlite() ? ISO_INSTANT : ISO_INSTANT_MICROS);
			assertThat(ref.get(field).asText()).as(field).isEqualTo(Instant.parse(ref.get(field).asText()).toString());
		}
		assertThat(ref.get("metadata").get("modified").asText()).matches(ISO_INSTANT);
	}

	@Test
	void testPublishedNanosecondPrecision() throws Exception {
		assertThat(postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"published\":\"2024-01-02T03:04:05.123456789Z\"}").getStatus()).isEqualTo(201);

		assertThat(getRef().get("published").asText())
			.isEqualTo(expectedStored("2024-01-02T03:04:05.123456789Z", "2024-01-02T03:04:05.123457Z"));
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow().getPublished())
			.isEqualTo(Instant.parse(expectedStored("2024-01-02T03:04:05.123456789Z", "2024-01-02T03:04:05.123457Z")));
	}

	@Test
	void testPublishedHalfMicrosecondPrecision() throws Exception {
		assertThat(postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"published\":\"2024-01-02T03:04:05.1234565Z\"}").getStatus()).isEqualTo(201);

		assertThat(getRef().get("published").asText())
			.isEqualTo(expectedStored("2024-01-02T03:04:05.123456500Z", "2024-01-02T03:04:05.123457Z"));
	}

	@Test
	void testPublishedMillisecondPrecision() throws Exception {
		assertThat(postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"published\":\"2024-01-02T03:04:05.100Z\"}").getStatus()).isEqualTo(201);

		assertThat(getRef().get("published").asText()).isEqualTo("2024-01-02T03:04:05.100Z");
	}

	@Test
	void testPublishedWholeSeconds() throws Exception {
		assertThat(postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"published\":\"2024-01-02T03:04:05Z\"}").getStatus()).isEqualTo(201);

		assertThat(getRef().get("published").asText()).isEqualTo("2024-01-02T03:04:05Z");
	}

	@Test
	void testPublishedOffsetNormalizedToUtc() throws Exception {
		assertThat(postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"published\":\"2024-01-02T03:04:05+01:00\"}").getStatus()).isEqualTo(201);

		assertThat(getRef().get("published").asText()).isEqualTo("2024-01-02T02:04:05Z");
	}

	@Test
	void testPublishedDecimalNumberIsEpochSeconds() throws Exception {
		assertThat(postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"published\":1700000000.123456789}").getStatus()).isEqualTo(201);

		assertThat(getRef().get("published").asText())
			.isEqualTo(expectedStored("2023-11-14T22:13:20.123456789Z", "2023-11-14T22:13:20.123457Z"));
	}

	@Test
	void testPublishedIntegerNumberIsEpochSeconds() throws Exception {
		assertThat(postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"published\":1700000000}").getStatus()).isEqualTo(201);

		assertThat(getRef().get("published").asText()).isEqualTo("2023-11-14T22:13:20Z");
	}

	@Test
	void testPublishedInvalidString() throws Exception {
		var response = postRef("{\"url\":\"" + URL + "\",\"published\":\"nope\"}");

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
		var problem = objectMapper.readTree(response.getContentAsString());
		assertThat(problem.get("message").asText()).isEqualTo("error.messageNotReadable");
		assertThat(problem.get("detail").asText()).isEqualTo(
			"Cannot deserialize value of type `java.time.Instant` from String \"nope\": Failed to deserialize java.time.Instant: " +
			"(java.time.format.DateTimeParseException) Text 'nope' could not be parsed at index 0" +
			MALFORMED_DETAIL_SUFFIX + "54] (through reference chain: jasper.domain.Ref[\"published\"])");
		assertThat(refRepository.count()).isZero();
	}

	@Test
	void testPostCursorMatchesStoredModified() throws Exception {
		var cursor = objectMapper.readValue(postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"]}").getContentAsString(), Instant.class);

		var stored = Instant.parse(getRef().get("modified").asText());
		if (sqlite()) {
			assertThat(stored).isEqualTo(cursor);
		} else {
			// PostgreSQL rounds to microseconds, so the returned cursor can differ from the stored value
			assertThat(stored.getNano() % 1000).isZero();
			assertThat(Duration.between(cursor, stored).abs()).isLessThanOrEqualTo(Duration.ofNanos(500));
		}
	}

	@Test
	void testPutWithPostCursor() throws Exception {
		var cursor = postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"]}").getContentAsString();

		var response = putRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"title\":\"Updated\",\"modified\":" + cursor + "}");

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentAsString()).matches("\"" + ISO_INSTANT + "\"");
		assertThat(getRef().get("title").asText()).isEqualTo("Updated");
	}

	@Test
	void testGetPutRoundTripKeepsInstants() throws Exception {
		assertThat(postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"title\":\"Before\",\"published\":\"2024-01-02T03:04:05.123456789Z\"}").getStatus()).isEqualTo(201);
		var before = (ObjectNode) getRef();
		var body = before.deepCopy();
		body.remove("metadata");
		body.put("title", "After");

		var response = putRef(objectMapper.writeValueAsString(body));

		assertThat(response.getStatus()).isEqualTo(200);
		var after = getRef();
		assertThat(after.get("title").asText()).isEqualTo("After");
		assertThat(after.get("published").asText()).isEqualTo(before.get("published").asText());
		assertThat(after.get("published").asText())
			.isEqualTo(expectedStored("2024-01-02T03:04:05.123456789Z", "2024-01-02T03:04:05.123457Z"));
		assertThat(after.get("created").asText()).isEqualTo(before.get("created").asText());
		assertThat(Instant.parse(after.get("modified").asText())).isAfter(Instant.parse(before.get("modified").asText()));
		var cursor = objectMapper.readValue(response.getContentAsString(), Instant.class);
		var modified = Instant.parse(after.get("modified").asText());
		if (sqlite()) {
			assertThat(modified).isEqualTo(cursor);
		} else {
			assertThat(modified.getNano() % 1000).isZero();
			assertThat(Duration.between(cursor, modified).abs()).isLessThanOrEqualTo(Duration.ofNanos(500));
		}
	}

	@Test
	void testGetPutRoundTripWithMetadataFails() throws Exception {
		assertThat(postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"title\":\"Before\"}").getStatus()).isEqualTo(201);
		var before = getRefBody(URL, "");

		// MetadataDto.responses is a count but Metadata.responses is a list, so a GET body can't be PUT back unchanged
		var response = putRef(before);

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
		var problem = objectMapper.readTree(response.getContentAsString());
		assertThat(problem.get("message").asText()).isEqualTo("error.messageNotReadable");
		assertThat(problem.get("detail").asText())
			.startsWith("Cannot deserialize value of type `java.util.ArrayList<java.lang.String>` from Integer value (token `JsonToken.VALUE_NUMBER_INT`)" + MALFORMED_DETAIL_SUFFIX)
			.endsWith("] (through reference chain: jasper.domain.Ref[\"metadata\"]->jasper.domain.Metadata[\"responses\"])");
		assertThat(getRef().get("title").asText()).isEqualTo("Before");
	}

	// Lenient reading

	@Test
	void testPostUnknownPropertyIgnored() throws Exception {
		var response = postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"title\":\"Unknown\",\"bogus\":{\"a\":1}}");

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(response.getContentAsString()).matches("\"" + ISO_INSTANT + "\"");
		var ref = getRef();
		assertThat(ref.get("title").asText()).isEqualTo("Unknown");
		assertThat(ref.has("bogus")).isFalse();
	}

	@Test
	void testPostReadOnlyFormulaPropertyIgnored() throws Exception {
		var response = postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"scheme\":\"ftp:\"}");

		assertThat(response.getStatus()).isEqualTo(201);
		var ref = getRef();
		assertThat(ref.has("scheme")).isFalse();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow().getScheme()).isEqualTo("https:");
	}

	@Test
	void testPostNullForPrimitiveMetadataFlag() throws Exception {
		var response = postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"metadata\":{\"obsolete\":null,\"regen\":null}}");

		assertThat(response.getStatus()).isEqualTo(201);
		var metadata = getRef().get("metadata");
		assertThat(metadata.get("obsolete").asBoolean(true)).isFalse();
		assertThat(metadata.get("regen").asBoolean(true)).isFalse();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow().getMetadata().isObsolete()).isFalse();
	}

	@Test
	void testPostNullOrigin() throws Exception {
		var response = postRef("{\"url\":\"" + URL + "\",\"origin\":null,\"tags\":[\"public\"]}");

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow().getOrigin()).isEqualTo("");
	}

	@Test
	void testPostTrailingObjectIgnored() throws Exception {
		// Jackson 3 enables FAIL_ON_TRAILING_TOKENS by default
		var response = postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"title\":\"Trailing\"} {\"title\":\"Second\"}");

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(getRef().get("title").asText()).isEqualTo("Trailing");
		assertThat(refRepository.count()).isEqualTo(1);
	}

	@Test
	void testPostTrailingGarbageIgnored() throws Exception {
		// Jackson 3 enables FAIL_ON_TRAILING_TOKENS by default
		var response = postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"title\":\"Trailing\"}x");

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(getRef().get("title").asText()).isEqualTo("Trailing");
		assertThat(refRepository.count()).isEqualTo(1);
	}

	@Test
	void testPostUnescapedControlCharacters() throws Exception {
		var response = postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"title\":\"a\tb\u0001c\"}");

		assertThat(response.getStatus()).isEqualTo(201);
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow().getTitle()).isEqualTo("a\tb\u0001c");
		var body = getRefBody(URL, "");
		assertThat(body).contains("\"title\":\"a\\tb\\u0001c\"");
		assertThat(objectMapper.readTree(body).get("title").asText()).isEqualTo("a\tb\u0001c");
	}

	@Test
	void testPostBackslashEscapingAnyCharacter() throws Exception {
		var response = postRef("{\"url\":\"" + URL + "\",\"tags\":[\"public\"],\"title\":\"x\\qy\",\"comment\":\"\\'\\%\\n\"}");

		assertThat(response.getStatus()).isEqualTo(201);
		var stored = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		assertThat(stored.getTitle()).isEqualTo("xqy");
		assertThat(stored.getComment()).isEqualTo("'%\n");
		var ref = getRef();
		assertThat(ref.get("title").asText()).isEqualTo("xqy");
		assertThat(ref.get("comment").asText()).isEqualTo("'%\n");
	}

	// Errors

	@Test
	void testMalformedJsonEndOfInput() throws Exception {
		var response = postRef("{\"url\":");

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
		var problem = objectMapper.readTree(response.getContentAsString());
		assertThat(fieldNames(problem)).containsExactly("type", "title", "status", "detail", "instance", "message", "path");
		assertThat(problem.get("type").asText()).isEqualTo("https://cjmalloy.github.io/jasper/docs/errors.html#request");
		assertThat(problem.get("title").asText()).isEqualTo("Bad Request");
		assertThat(problem.get("status").isInt()).isTrue();
		assertThat(problem.get("status").asInt()).isEqualTo(400);
		assertThat(problem.get("detail").asText()).isEqualTo("Unexpected end-of-input within/between Object entries" + MALFORMED_DETAIL_SUFFIX + "8]");
		assertThat(problem.get("instance").asText()).isEqualTo("/api/v1/ref");
		assertThat(problem.get("message").asText()).isEqualTo("error.messageNotReadable");
		assertThat(problem.get("path").asText()).isEqualTo("/api/v1/ref");
		assertThat(refRepository.count()).isZero();
	}

	@Test
	void testMalformedJsonMissingColon() throws Exception {
		var response = postRef("{\"url\" \"x\"}");

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
		var problem = objectMapper.readTree(response.getContentAsString());
		assertThat(fieldNames(problem)).containsExactly("type", "title", "status", "detail", "instance", "message", "path");
		assertThat(problem.get("title").asText()).isEqualTo("Bad Request");
		assertThat(problem.get("status").asInt()).isEqualTo(400);
		assertThat(problem.get("detail").asText()).isEqualTo(
			"Unexpected character ('\"' (code 34)): was expecting a colon to separate field name and value" + MALFORMED_DETAIL_SUFFIX + "8]");
		assertThat(problem.get("message").asText()).isEqualTo("error.messageNotReadable");
	}

	@Test
	void testEmptyBody() throws Exception {
		var response = postRef("");

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
		var problem = objectMapper.readTree(response.getContentAsString());
		assertThat(fieldNames(problem)).containsExactly("type", "title", "status", "detail", "instance", "message", "path");
		assertThat(problem.get("title").asText()).isEqualTo("Bad Request");
		assertThat(problem.get("message").asText()).isEqualTo("error.messageNotReadable");
	}

	// Property order

	@Test
	void testRefPropertyOrder() throws Exception {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin("@a");
		ref.setTitle("Title");
		ref.setComment("Comment");
		ref.setTags(new ArrayList<>(List.of("public", "plugin/test")));
		ref.setSources(new ArrayList<>(List.of("https://www.example.com/source")));
		ref.setAlternateUrls(new ArrayList<>(List.of("https://www.example.com/alt")));
		ref.setPlugins((ObjectNode) objectMapper.readTree("{\"plugin/test\":{\"b\":1,\"a\":2}}"));
		var metadata = new Metadata();
		metadata.setModified("2024-01-02T03:04:05Z");
		metadata.setResponses(new ArrayList<>(List.of("https://www.example.com/r")));
		ref.setMetadata(metadata);
		ref.setPublished(Instant.parse("2024-01-02T03:04:05Z"));
		refRepository.save(ref);

		var body = getRefBody(URL, "@a");

		var tree = objectMapper.readTree(body);
		assertThat(fieldNames(tree)).containsExactly(
			"url", "origin", "title", "comment", "tags", "sources", "alternateUrls", "plugins", "metadata", "published", "created", "modified");
		assertThat(fieldNames(tree.get("metadata"))).containsExactly(
			"modified", "responses", "internalResponses", "obsolete", "regen", "cascade");
		// SQLite keeps insertion order, PostgreSQL jsonb sorts keys
		assertThat(fieldNames(tree.get("plugins").get("plugin/test"))).containsExactly(sqlite() ? new String[]{"b", "a"} : new String[]{"a", "b"});
		assertThat(tree.get("origin").asText()).isEqualTo("@a");
		assertThat(tree.get("published").asText()).isEqualTo("2024-01-02T03:04:05Z");
	}

	@Test
	void testExtPropertyOrder() throws Exception {
		var ext = new Ext();
		ext.setTag("ext");
		ext.setOrigin("@a");
		ext.setName("Name");
		ext.setConfig((ObjectNode) objectMapper.readTree("{\"z\":1,\"a\":2}"));
		extRepository.save(ext);

		var response = mockMvc
			.perform(get("/api/v1/ext").param("tag", "ext@a"))
			.andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(200);
		var tree = objectMapper.readTree(response.getContentAsString());
		assertThat(fieldNames(tree)).containsExactly("tag", "origin", "name", "config", "modified");
		// SQLite keeps insertion order, PostgreSQL jsonb sorts keys
		assertThat(fieldNames(tree.get("config"))).containsExactly(sqlite() ? new String[]{"z", "a"} : new String[]{"a", "z"});
		assertThat(tree.get("modified").asText()).matches(sqlite() ? ISO_INSTANT : ISO_INSTANT_MICROS);
	}

	@Test
	void testExtLocalOriginOmitted() throws Exception {
		var ext = new Ext();
		ext.setTag("ext");
		ext.setName("Name");
		extRepository.save(ext);

		var response = mockMvc
			.perform(get("/api/v1/ext").param("tag", "ext"))
			.andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(200);
		// NON_EMPTY: empty origin and null config are omitted
		assertThat(fieldNames(objectMapper.readTree(response.getContentAsString()))).containsExactly("tag", "name", "modified");
	}
}
