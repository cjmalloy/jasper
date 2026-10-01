package jasper.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginDataTest {

	PluginData pluginData = new PluginData();
	ObjectMapper mapper = new ObjectMapper();

	ObjectNode obj(String json) throws IOException {
		return (ObjectNode) mapper.readTree(json);
	}

	@Test
	void testMergeNestedObjects() throws IOException {
		var result = pluginData.merge(
			obj("{\"a\": {\"x\": 1, \"y\": 2}, \"b\": 1}"),
			obj("{\"a\": {\"y\": 3, \"z\": 4}, \"c\": 5}"));

		assertThat(result).isEqualTo(obj("{\"a\": {\"x\": 1, \"y\": 3, \"z\": 4}, \"b\": 1, \"c\": 5}"));
	}

	@Test
	void testMergeScalarReplacesObject() throws IOException {
		var result = pluginData.merge(
			obj("{\"a\": {\"x\": 1}, \"b\": [1, 2]}"),
			obj("{\"a\": \"scalar\", \"b\": [3]}"));

		assertThat(result).isEqualTo(obj("{\"a\": \"scalar\", \"b\": [3]}"));
	}

	@Test
	void testMergeNulls() throws IOException {
		var a = obj("{\"a\": 1}");

		assertThat(pluginData.merge(null, null)).isEqualTo(obj("{}"));
		assertThat(pluginData.merge(a, null)).isEqualTo(a).isNotSameAs(a);
		assertThat(pluginData.merge(null, a)).isEqualTo(a).isNotSameAs(a);
	}

	@Test
	void testMergeDoesNotModifyInputs() throws IOException {
		var a = obj("{\"a\": {\"x\": 1, \"n\": {\"deep\": true}}, \"b\": 1}");
		var b = obj("{\"a\": {\"x\": 2, \"n\": {\"other\": false}}, \"c\": {\"y\": 3}}");
		var aCopy = a.deepCopy();
		var bCopy = b.deepCopy();

		var result = pluginData.merge(a, b);
		((ObjectNode) result.get("c")).put("y", 99);
		((ObjectNode) result.get("a").get("n")).put("deep", false);

		assertThat(a).isEqualTo(aCopy);
		assertThat(b).isEqualTo(bCopy);
	}

	@Test
	void testFillerElements() throws IOException {
		assertThat(PluginData.filler(obj("{\"elements\": {\"type\": \"string\"}}")))
			.isEqualTo(mapper.createArrayNode());
	}

	@Test
	void testFillerObjectForms() throws IOException {
		assertThat(PluginData.filler(obj("{\"properties\": {}}"))).isEqualTo(obj("{}"));
		assertThat(PluginData.filler(obj("{\"optionalProperties\": {}}"))).isEqualTo(obj("{}"));
		assertThat(PluginData.filler(obj("{\"values\": {\"type\": \"string\"}}"))).isEqualTo(obj("{}"));
		assertThat(PluginData.filler(obj("{\"discriminator\": \"kind\", \"mapping\": {}}"))).isEqualTo(obj("{}"));
	}

	@Test
	void testFillerNone() throws IOException {
		assertThat(PluginData.filler(null)).isNull();
		assertThat(PluginData.filler(obj("{}"))).isNull();
		assertThat(PluginData.filler(obj("{\"type\": \"string\"}"))).isNull();
		assertThat(PluginData.filler(obj("{\"enum\": [\"a\", \"b\"]}"))).isNull();
		assertThat(PluginData.filler(obj("{\"unknown\": true}"))).isNull();
	}

	@Test
	void testFillerRef() throws IOException {
		assertThat(PluginData.filler(obj("""
			{"definitions": {"list": {"elements": {"type": "string"}}}, "ref": "list"}""")))
			.isEqualTo(mapper.createArrayNode());
		assertThat(PluginData.filler(obj("""
			{"definitions": {"a": {"ref": "b"}, "b": {"values": {"type": "string"}}}, "ref": "a"}""")))
			.isEqualTo(obj("{}"));
		assertThat(PluginData.filler(obj("""
			{"definitions": {"s": {"type": "string"}}, "ref": "s"}""")))
			.isNull();
		assertThat(PluginData.filler(obj("{\"ref\": \"missing\"}"))).isNull();
	}

	@Test
	void testFillerRefCycle() throws IOException {
		assertThat(PluginData.filler(obj("""
			{"definitions": {"a": {"ref": "b"}, "b": {"ref": "a"}}, "ref": "a"}""")))
			.isNull();
		assertThat(PluginData.filler(obj("""
			{"definitions": {"a": {"ref": "a"}}, "ref": "a"}""")))
			.isNull();
	}

	@Test
	void testDropUntouchedSeeds() throws IOException {
		var seeded = new PluginData.Seeded(obj("{}"), Map.<String, JsonNode>of(
			"plugin/obj", obj("{}"),
			"plugin/list", mapper.createArrayNode(),
			"plugin/changed", obj("{}")));
		var patched = obj("""
			{"plugin/obj": {}, "plugin/list": [], "plugin/changed": {"color": "red"}, "plugin/other": {}}""");

		seeded.dropUntouchedSeeds(patched);

		assertThat(patched).isEqualTo(obj("{\"plugin/changed\": {\"color\": \"red\"}, \"plugin/other\": {}}"));
	}
}
