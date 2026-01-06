package jasper.util;

import com.jsontypedef.jtd.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An implementation of {@code Json} for Jackson 3.
 */
public class Jackson3Adapter implements Json {
	private JsonNode jsonNode;

	/**
	 * Constructs a {@code JacksonAdapter} that wraps a Jackson {@code JsonNode}.
	 *
	 * @param jsonNode the Jackson value to wrap
	 */
	public Jackson3Adapter(JsonNode jsonNode) {
		this.jsonNode = jsonNode;
	}

	@Override
	public boolean isNull() {
		return jsonNode.isNull();
	}

	@Override
	public boolean isBoolean() {
		return jsonNode.isBoolean();
	}

	@Override
	public boolean isNumber() {
		return jsonNode.isNumber();
	}

	@Override
	public boolean isString() {
		return jsonNode.isString();
	}

	@Override
	public boolean isArray() {
		return jsonNode.isArray();
	}

	@Override
	public boolean isObject() {
		return jsonNode.isObject();
	}

	@Override
	public boolean asBoolean() {
		return jsonNode.asBoolean();
	}

	@Override
	public double asNumber() {
		return jsonNode.asDouble();
	}

	@Override
	public String asString() {
		return jsonNode.asString();
	}

	@Override
	public List<Json> asArray() {
		List<Json> arr = new ArrayList<>();
		for (JsonNode node : jsonNode) {
			arr.add(new Jackson3Adapter(node));
		}

		return arr;
	}

	@Override
	public Map<String, Json> asObject() {
		Map<String, Json> obj = new HashMap<>();
		jsonNode.propertyStream().forEach(entry -> obj.put(entry.getKey(), new Jackson3Adapter(entry.getValue())));

		return obj;
	}
}
