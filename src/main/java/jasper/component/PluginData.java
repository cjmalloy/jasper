package jasper.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static jasper.component.Meta.expandTags;

@Component
public class PluginData {

	@Autowired
	ConfigCache configs;

	/**
	 * Recursively merge b over a. Never modifies the inputs.
	 */
	public ObjectNode merge(ObjectNode a, ObjectNode b) {
		if (a == null && b == null) return JsonNodeFactory.instance.objectNode();
		if (a == null) return b.deepCopy();
		if (b == null) return a.deepCopy();
		var result = a.deepCopy();
		b.fieldNames().forEachRemaining(field -> {
			var aNode = result.get(field);
			var bNode = b.get(field);
			if (aNode instanceof ObjectNode aObj && bNode instanceof ObjectNode bObj) {
				result.set(field, merge(aObj, bObj));
			} else {
				result.set(field, bNode.deepCopy());
			}
		});
		return result;
	}

	/**
	 * Plugin defaults for every expanded tag, with the Ref's existing plugins merged over them.
	 */
	public ObjectNode defaults(String origin, Ref ref) {
		var result = JsonNodeFactory.instance.objectNode();
		for (var tag : expandTags(ref.getTags())) {
			configs.getPlugin(tag, origin)
				.map(Plugin::getDefaults)
				.filter(d -> !d.isNull() && (d.isValueNode() || !d.isEmpty()))
				.ifPresent(d -> result.set(tag, d.deepCopy()));
		}
		return merge(result, ref.getPlugins());
	}

	/**
	 * Starting data for a patch: defaults, plus an empty object or array for
	 * any schema plugin that still has no data. Computed once from the state
	 * before the patch.
	 */
	public Seeded seed(String origin, Ref ref) {
		var data = defaults(origin, ref);
		var seeds = new HashMap<String, JsonNode>();
		for (var tag : expandTags(ref.getTags())) {
			if (data.hasNonNull(tag)) continue;
			configs.getPlugin(tag, origin)
				.map(Plugin::getSchema)
				.map(PluginData::filler)
				.ifPresent(f -> {
					data.set(tag, f.deepCopy());
					seeds.put(tag, f);
				});
		}
		return new Seeded(data, seeds);
	}

	static JsonNode filler(JsonNode schema) {
		if (schema == null) return null;
		var definitions = schema.get("definitions");
		var seen = new HashSet<String>();
		var s = schema;
		while (s != null && s.has("ref")) {
			var name = s.get("ref").asText();
			if (!seen.add(name) || definitions == null) return null;
			s = definitions.get(name);
		}
		if (s == null) return null;
		if (s.has("elements")) return JsonNodeFactory.instance.arrayNode();
		if (s.has("properties") || s.has("optionalProperties") || s.has("values") || s.has("discriminator")) {
			return JsonNodeFactory.instance.objectNode();
		}
		return null;
	}

	public record Seeded(ObjectNode data, Map<String, JsonNode> seeds) {
		/**
		 * Remove filler values the patch did not touch, so they are not stored.
		 */
		public void dropUntouchedSeeds(ObjectNode patched) {
			if (patched == null) return;
			seeds.forEach((tag, seed) -> {
				if (seed.equals(patched.get(tag))) patched.remove(tag);
			});
		}
	}
}
