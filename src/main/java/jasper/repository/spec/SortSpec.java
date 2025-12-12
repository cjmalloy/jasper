package jasper.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

import java.util.regex.Pattern;

import static java.util.Arrays.stream;

/**
 * Shared utility class for JSONB sorting logic.
 * Used by entity-specific Spec classes (RefSpec, ExtSpec, UserSpec, PluginSpec, TemplateSpec).
 */
public class SortSpec {

	private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("\\[(\\d+)]");

	/**
	 * Creates a JSONB sort expression for the given property path.
	 * Supports ":num" suffix for numeric sorting and ":len" suffix for array length sorting.
	 * Uses COALESCE to handle nulls (0 for numeric/length, '' for string).
	 *
	 * @param root the query root
	 * @param cb the criteria builder
	 * @param property the sort property (e.g., "config->field->subfield:num")
	 * @param prefixes list of allowed JSONB field prefixes (e.g., ["config", "defaults", "schema"])
	 * @return the sort expression, or null if property doesn't match allowed prefixes
	 */
	public static Expression<?> createJsonbSortExpression(Root<?> root, CriteriaBuilder cb, String property, String... prefixes) {
		var numericSort = property.endsWith(":num");
		var lengthSort = property.endsWith(":len");
		if (property.contains(":")) property = property.substring(0, property.lastIndexOf(":"));
		var parts = property.split("->");
		if (parts.length < 2) return null;
		var jsonbFieldName = parts[0];
		if (stream(prefixes).noneMatch(p -> jsonbFieldName.equals(p))) return null;

		Expression<?> expr = root.get(jsonbFieldName);
		for (int i = 1; i < parts.length; i++) {
			var field = parts[i];
			// Check for array index notation like "ids[0]"
			var matcher = ARRAY_INDEX_PATTERN.matcher(field);
			if (matcher.find()) {
				var fieldName = field.substring(0, matcher.start());
				var indexStr = matcher.group(1);
				if (indexStr == null || !indexStr.matches("\\d+")) throw new IllegalArgumentException("Invalid array index in field: '" + field + "'");
				var index = Integer.parseInt(indexStr);
				if (!fieldName.isEmpty()) {
					expr = cb.function("jsonb_object_field", Object.class, expr, cb.literal(fieldName));
				}
				expr = cb.function("jsonb_array_element_text", String.class, expr, cb.literal(index));
			} else if (i == parts.length - 1 && lengthSort) {
				// Last field with length sort - get as JSONB and apply jsonb_array_length
				expr = cb.function("jsonb_object_field", Object.class, expr, cb.literal(field));
				return cb.coalesce(cb.function("jsonb_array_length", Integer.class, expr), cb.literal(0));
			} else if (i == parts.length - 1) {
				// Last field - get as text
				expr = cb.function("jsonb_object_field_text", String.class, expr, cb.literal(field));
			} else {
				// Intermediate field - get as JSONB object
				expr = cb.function("jsonb_object_field", Object.class, expr, cb.literal(field));
			}
		}
		if (numericSort) {
			return cb.coalesce(cb.function("cast_to_numeric", Double.class, expr), cb.literal(0.0));
		} else {
			return cb.coalesce(expr, cb.literal(""));
		}
	}

	/**
	 * Checks if a property is a JSONB sort property.
	 */
	public static boolean isJsonbSortProperty(String property, String... prefixes) {
		return stream(prefixes).anyMatch(p -> property.startsWith(p + "->"));
	}

	/**
	 * Handles origin:len sorting (origin nesting level).
	 */
	public static Expression<?> createOriginNestingExpression(Root<?> root, CriteriaBuilder cb) {
		return cb.function("origin_nesting", Integer.class, root.get("origin"));
	}

	/**
	 * Handles tag:len sorting (tag nesting levels).
	 */
	public static Expression<?> createTagLevelsExpression(Root<?> root, CriteriaBuilder cb) {
		return cb.function("tag_levels", Integer.class, root.get("tag"));
	}

	/**
	 * Handles direct array field length sorting (e.g., "tags:len", "sources:len").
	 */
	public static Expression<?> createArrayLengthExpression(Root<?> root, CriteriaBuilder cb, String fieldName) {
		return cb.coalesce(cb.function("jsonb_array_length", Integer.class, root.get(fieldName)), cb.literal(0));
	}
}
