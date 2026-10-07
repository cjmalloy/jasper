package jasper.component;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.jsontypedef.jtd.Schema;
import com.jsontypedef.jtd.ValidationError;
import jasper.errors.FieldError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts JTD and Jackson validation errors into {@link FieldError}s.
 * Messages never contain the submitted value.
 */
public final class SchemaErrors {

	private SchemaErrors() {}

	public static List<FieldError> of(Schema schema, JsonNode instance, List<ValidationError> errors) {
		return errors.stream().map(e -> of(schema, instance, e)).toList();
	}

	/**
	 * Interpret a JTD error by walking its schema path from the root schema (RFC 8927).
	 */
	public static FieldError of(Schema root, JsonNode instance, ValidationError error) {
		var path = new ArrayList<>(error.getInstancePath());
		var sp = error.getSchemaPath();
		var node = root;
		String code = null;
		String expected = null;
		var i = 0;
		while (i < sp.size() && node != null) {
			var last = i == sp.size() - 1;
			switch (sp.get(i)) {
				case "definitions" -> {
					node = last || root.getDefinitions() == null ? null : root.getDefinitions().get(sp.get(i + 1));
					i += 2;
				}
				case "properties", "optionalProperties" -> {
					if (last) {
						// Not an object
						code = "type";
						expected = "object";
						i++;
					} else {
						var props = sp.get(i).equals("properties") ? node.getProperties() : node.getOptionalProperties();
						var key = sp.get(i + 1);
						node = props == null ? null : props.get(key);
						i += 2;
						if (i == sp.size()) {
							code = "missing";
							expected = describe(node);
							path.add(key);
						}
					}
				}
				case "elements" -> {
					if (last) {
						code = "elements";
						expected = "array";
					} else {
						node = node.getElements();
					}
					i++;
				}
				case "values" -> {
					if (last) {
						code = "values";
						expected = "object";
					} else {
						node = node.getValues();
					}
					i++;
				}
				case "mapping" -> {
					if (last) {
						code = "discriminator";
						expected = keys(node.getMapping());
					} else {
						node = node.getMapping() == null ? null : node.getMapping().get(sp.get(i + 1));
					}
					i += 2;
				}
				case "discriminator" -> {
					code = "discriminator";
					expected = keys(node.getMapping());
					i++;
				}
				case "type" -> {
					code = "type";
					expected = describe(node);
					i++;
				}
				case "enum" -> {
					code = "enum";
					expected = keys(node.getEnum());
					i++;
				}
				default -> i++;
			}
		}
		// Additional properties are reported with the schema path of the object schema
		if (code == null) code = "unexpected";
		if (!code.equals("missing") && !code.equals("unexpected")) {
			var value = at(instance, path);
			if (value != null && value.isNull()) code = "nullable";
		}
		return new FieldError(pointer(path), pointer(sp), code, expected, message(path, code, expected));
	}

	/**
	 * Report the field that failed to bind from a Jackson error in the cause chain.
	 */
	public static List<FieldError> of(Throwable e) {
		for (var t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t instanceof JsonMappingException m) {
				var path = m.getPath().stream()
					.map(r -> r.getFieldName() != null ? r.getFieldName() : String.valueOf(r.getIndex()))
					.toList();
				String code = "invalid";
				String expected = null;
				if (m instanceof UnrecognizedPropertyException) {
					code = "unexpected";
				} else if (m instanceof MismatchedInputException mi && mi.getTargetType() != null) {
					code = "type";
					expected = mi.getTargetType().getSimpleName();
				}
				return List.of(new FieldError(pointer(path), null, code, expected, message(path, code, expected)));
			}
		}
		return List.of();
	}

	private static String describe(Schema node) {
		if (node == null) return null;
		if (node.getType() != null) return node.getType().name().toLowerCase();
		if (node.getEnum() != null) return keys(node.getEnum());
		if (node.getElements() != null) return "array";
		if (node.getValues() != null || node.getProperties() != null || node.getOptionalProperties() != null || node.getDiscriminator() != null) return "object";
		return null;
	}

	private static String keys(Map<String, ?> map) {
		return map == null ? null : keys(map.keySet());
	}

	private static String keys(Collection<String> values) {
		return values == null ? null : values.stream().sorted().collect(Collectors.joining(", "));
	}

	private static JsonNode at(JsonNode instance, List<String> path) {
		var node = instance;
		for (var p : path) {
			if (node == null) return null;
			if (node.isArray()) {
				try {
					node = node.get(Integer.parseInt(p));
				} catch (NumberFormatException e) {
					return null;
				}
			} else {
				node = node.get(p);
			}
		}
		return node;
	}

	static String pointer(List<String> path) {
		var result = new StringBuilder();
		for (var p : path) result.append('/').append(p.replace("~", "~0").replace("/", "~1"));
		return result.toString();
	}

	private static String message(List<String> path, String code, String expected) {
		var label = path.isEmpty() ? "value" : String.join("/", path);
		return label + ": " + switch (code) {
			case "missing" -> "required";
			case "unexpected" -> "not allowed";
			case "nullable" -> "must not be null";
			case "enum", "discriminator" -> expected == null ? "invalid " + code : "expected one of " + expected;
			case "type", "elements", "values" -> expected == null ? "invalid " + code : "expected " + expected;
			default -> "invalid";
		};
	}
}
