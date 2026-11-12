package jasper.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JSON shape of {@link Metadata} as written by the application mapper
 * and as exposed through the REST API (via MetadataDto).
 */
@WithMockUser(value = "+user/tester", roles = {"ADMIN"})
@AutoConfigureMockMvc
@IntegrationTest
class MetadataJsonIT {

	private static final String MODIFIED = "2024-01-02T03:04:05Z";
	private static final String URL = "https://www.example.com/metadata";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RefRepository refRepository;

	@BeforeEach
	void setup() {
		refRepository.deleteAll();
	}

	private Metadata metadata() {
		var metadata = new Metadata();
		metadata.setModified(MODIFIED);
		return metadata;
	}

	@Test
	void testDefaultMetadataJson() throws Exception {
		assertThat(objectMapper.writeValueAsString(metadata()))
			.isEqualTo("{\"modified\":\"2024-01-02T03:04:05Z\"}");
	}

	@Test
	void testDefaultMetadataHasOnlyModified() throws Exception {
		var tree = objectMapper.readTree(objectMapper.writeValueAsString(new Metadata()));
		assertThat(fieldNames(tree)).containsExactly("modified");
		assertThat(tree.get("modified").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.(\\d{3}){1,3})?Z");

		var built = objectMapper.readTree(objectMapper.writeValueAsString(Metadata.builder().build()));
		assertThat(fieldNames(built)).containsExactly("modified");
	}

	@Test
	void testNullModifiedIsEmptyObject() throws Exception {
		var metadata = metadata();
		metadata.setModified(null);
		assertThat(objectMapper.writeValueAsString(metadata)).isEqualTo("{}");
	}

	@Test
	void testEmptyCollectionsOmitted() throws Exception {
		var metadata = metadata();
		metadata.setNewResponse("");
		metadata.setExpandedTags(List.of());
		metadata.setResponses(List.of());
		metadata.setInternalResponses(List.of());
		metadata.setPlugins(Map.of());
		metadata.setRemotePlugins(Map.of());
		metadata.setUserUrls(Map.of());
		assertThat(objectMapper.writeValueAsString(metadata))
			.isEqualTo("{\"modified\":\"2024-01-02T03:04:05Z\"}");
	}

	@Test
	void testFalseFlagsOmitted() throws Exception {
		var metadata = metadata();
		metadata.setObsolete(false);
		metadata.setRegen(false);
		metadata.setCascade(false);
		metadata.setIgnored(false);
		assertThat(objectMapper.writeValueAsString(metadata))
			.isEqualTo("{\"modified\":\"2024-01-02T03:04:05Z\"}");
	}

	@Test
	void testObsoleteFlagJson() throws Exception {
		assertFlag(m -> m.setObsolete(true), "obsolete");
	}

	@Test
	void testRegenFlagJson() throws Exception {
		assertFlag(m -> m.setRegen(true), "regen");
	}

	@Test
	void testCascadeFlagJson() throws Exception {
		assertFlag(m -> m.setCascade(true), "cascade");
	}

	@Test
	void testIgnoredFlagJson() throws Exception {
		assertFlag(m -> m.setIgnored(true), "ignored");
	}

	private void assertFlag(Consumer<Metadata> set, String key) throws Exception {
		var metadata = metadata();
		set.accept(metadata);
		assertThat(objectMapper.writeValueAsString(metadata))
			.isEqualTo("{\"modified\":\"2024-01-02T03:04:05Z\",\"" + key + "\":true}");
	}

	@Test
	void testAllFieldsJsonOrder() throws Exception {
		var metadata = Metadata.builder()
			.modified(MODIFIED)
			.newResponse("2024-01-02T03:04:06.000000000Z")
			.newReaction("2024-01-02T03:04:07.000000000Z")
			.expandedTags(List.of("public"))
			.responses(List.of("https://www.example.com/r"))
			.internalResponses(List.of("https://www.example.com/i"))
			.plugins(Map.of("plugin/comment", 1L))
			.remotePlugins(Map.of("plugin/comment", 2L))
			.userUrls(Map.of("plugin/user/vote", List.of("tag:/+user/a")))
			.obsolete(true)
			.regen(true)
			.cascade(true)
			.ignored(true)
			.build();
		assertThat(objectMapper.writeValueAsString(metadata)).isEqualTo(
			"{\"modified\":\"2024-01-02T03:04:05Z\"," +
			"\"newResponse\":\"2024-01-02T03:04:06.000000000Z\"," +
			"\"newReaction\":\"2024-01-02T03:04:07.000000000Z\"," +
			"\"expandedTags\":[\"public\"]," +
			"\"responses\":[\"https://www.example.com/r\"]," +
			"\"internalResponses\":[\"https://www.example.com/i\"]," +
			"\"plugins\":{\"plugin/comment\":1}," +
			"\"remotePlugins\":{\"plugin/comment\":2}," +
			"\"userUrls\":{\"plugin/user/vote\":[\"tag:/+user/a\"]}," +
			"\"obsolete\":true,\"regen\":true,\"cascade\":true,\"ignored\":true}");
	}

	@Test
	void testDeserializeFlags() throws Exception {
		var metadata = objectMapper.readValue(
			"{\"modified\":\"m\",\"obsolete\":true,\"regen\":true,\"cascade\":true,\"ignored\":true}",
			Metadata.class);
		assertThat(metadata.getModified()).isEqualTo("m");
		assertThat(metadata.isObsolete()).isTrue();
		assertThat(metadata.isRegen()).isTrue();
		assertThat(metadata.isCascade()).isTrue();
		assertThat(metadata.isIgnored()).isTrue();
	}

	@Test
	void testDeserializeNullForPrimitiveFlag() throws Exception {
		// A null flag reads as unset (Jackson 3 enables FAIL_ON_NULL_FOR_PRIMITIVES by default)
		var metadata = objectMapper.readValue(
			"{\"modified\":\"m\",\"obsolete\":null,\"regen\":null,\"cascade\":null,\"ignored\":null}",
			Metadata.class);
		assertThat(metadata.isObsolete()).isFalse();
		assertThat(metadata.isRegen()).isFalse();
		assertThat(metadata.isCascade()).isFalse();
		assertThat(metadata.isIgnored()).isFalse();
	}

	@Test
	void testDeserializeEmptyObjectUsesFieldDefaults() throws Exception {
		var metadata = objectMapper.readValue("{}", Metadata.class);
		assertThat(metadata.getModified()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.(\\d{3}){1,3})?Z");
		assertThat(metadata.getResponses()).isNull();
		assertThat(metadata.getPlugins()).isNull();
		assertThat(metadata.isObsolete()).isFalse();
		assertThat(metadata.isIgnored()).isFalse();
	}

	@Test
	void testDeserializeUnknownPropertyIgnored() throws Exception {
		var metadata = objectMapper.readValue("{\"modified\":\"m\",\"bogus\":1}", Metadata.class);
		assertThat(metadata.getModified()).isEqualTo("m");
	}

	@Test
	void testDeserializeExplicitNullModified() throws Exception {
		var metadata = objectMapper.readValue("{\"modified\":null}", Metadata.class);
		assertThat(metadata.getModified()).isNull();
	}

	private Ref saveRef(Metadata metadata) {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTags(new ArrayList<>(List.of("public")));
		ref.setMetadata(metadata);
		return refRepository.save(ref);
	}

	private JsonNode getMetadata() throws Exception {
		var body = mockMvc
			.perform(get("/api/v1/ref").param("url", URL))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("metadata");
	}

	@Test
	void testStoredDefaultMetadataJson() throws Exception {
		saveRef(metadata());
		var stored = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		assertThat(objectMapper.writeValueAsString(stored.getMetadata()))
			.isEqualTo("{\"modified\":\"2024-01-02T03:04:05Z\"}");
		assertThat(stored.getMetadata().isObsolete()).isFalse();
	}

	@Test
	void testGetRefDefaultMetadataShape() throws Exception {
		saveRef(metadata());
		// MetadataDto is NON_EMPTY on primitives: unlike Metadata, false flags and zero counts are written
		assertThat(objectMapper.writeValueAsString(getMetadata())).isEqualTo(
			"{\"modified\":\"2024-01-02T03:04:05Z\",\"responses\":0,\"internalResponses\":0," +
			"\"obsolete\":false,\"regen\":false,\"cascade\":false}");
	}

	@Test
	void testGetRefWithoutMetadata() throws Exception {
		saveRef(null);
		var body = mockMvc
			.perform(get("/api/v1/ref").param("url", URL))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		var tree = objectMapper.readTree(body);
		assertThat(tree.has("metadata")).isFalse();
	}

	@Test
	void testGetRefObsoleteMetadata() throws Exception {
		var metadata = metadata();
		metadata.setObsolete(true);
		saveRef(metadata);
		assertThat(objectMapper.writeValueAsString(getMetadata())).isEqualTo(
			"{\"modified\":\"2024-01-02T03:04:05Z\",\"responses\":0,\"internalResponses\":0," +
			"\"obsolete\":true,\"regen\":false,\"cascade\":false}");
	}

	@Test
	void testGetRefRegenMetadata() throws Exception {
		var metadata = metadata();
		metadata.setRegen(true);
		saveRef(metadata);
		assertThat(objectMapper.writeValueAsString(getMetadata())).isEqualTo(
			"{\"modified\":\"2024-01-02T03:04:05Z\",\"responses\":0,\"internalResponses\":0," +
			"\"obsolete\":false,\"regen\":true,\"cascade\":false}");
	}

	@Test
	void testGetRefCascadeMetadata() throws Exception {
		var metadata = metadata();
		metadata.setCascade(true);
		saveRef(metadata);
		assertThat(objectMapper.writeValueAsString(getMetadata())).isEqualTo(
			"{\"modified\":\"2024-01-02T03:04:05Z\",\"responses\":0,\"internalResponses\":0," +
			"\"obsolete\":false,\"regen\":false,\"cascade\":true}");
	}

	@Test
	void testGetRefIgnoredMetadataNotExposed() throws Exception {
		var metadata = metadata();
		metadata.setIgnored(true);
		saveRef(metadata);
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow().getMetadata().isIgnored()).isTrue();
		// MetadataDto has no ignored property
		assertThat(objectMapper.writeValueAsString(getMetadata())).isEqualTo(
			"{\"modified\":\"2024-01-02T03:04:05Z\",\"responses\":0,\"internalResponses\":0," +
			"\"obsolete\":false,\"regen\":false,\"cascade\":false}");
	}

	@Test
	void testGetRefAllFlagsMetadata() throws Exception {
		var metadata = metadata();
		metadata.setObsolete(true);
		metadata.setRegen(true);
		metadata.setCascade(true);
		metadata.setIgnored(true);
		metadata.setResponses(new ArrayList<>(List.of("https://www.example.com/r1", "https://www.example.com/r2")));
		metadata.setInternalResponses(new ArrayList<>(List.of("https://www.example.com/i")));
		saveRef(metadata);
		assertThat(objectMapper.writeValueAsString(getMetadata())).isEqualTo(
			"{\"modified\":\"2024-01-02T03:04:05Z\",\"responses\":2,\"internalResponses\":1," +
			"\"obsolete\":true,\"regen\":true,\"cascade\":true}");
	}

	private static List<String> fieldNames(JsonNode node) {
		var result = new ArrayList<String>();
		node.fieldNames().forEachRemaining(result::add);
		return result;
	}
}
