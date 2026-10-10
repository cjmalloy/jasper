package jasper.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jasper.domain.proj.Tag;

import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Arrays.stream;

/**
 * Shared utility class for JSONB sorting logic.
 * Used by entity-specific Spec classes (RefSpec, ExtSpec, UserSpec, PluginSpec, TemplateSpec).
 */
public class SortSpec {

	private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("\\[(\\d+)]");

	/**
	 * Allowed metadata fields for sorting.
	 * Only these fields can be accessed under metadata->.
	 */
	private static final Set<String> ALLOWED_METADATA_FIELDS = Set.of(
		"modified", "newResponse", "newReaction", "expandedTags", "responses", "internalResponses", "plugins"
	);

	/**
	 * Metadata fields that should automatically use :len suffix (array fields).
	 */
	private static final Set<String> METADATA_LEN_FIELDS = Set.of(
		"expandedTags", "responses", "internalResponses"
	);

	/**
	 * Metadata fields that should automatically use :num suffix (numeric fields).
	 */
	private static final Set<String> METADATA_NUM_FIELDS = Set.of(
	);

	/**
	 * Creates a JSONB sort expression for the given property path.
	 * Supports ":num" suffix for numeric sorting and ":len" suffix for array length sorting.
	 * Uses COALESCE to handle nulls (0 for numeric/length, '' for string).
	 *
	 * For metadata fields, automatically applies the correct suffix and restricts access
	 * to only allowed fields: modified, newResponse, newReaction, expandedTags, responses, internalResponses, plugins.
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
		if (stream(prefixes).noneMatch(jsonbFieldName::equals)) return null;

		// Special handling for metadata prefix
		if ("metadata".equals(jsonbFieldName)) {
			var metadataField = parts[1];
			// Only allow access to specific metadata fields
			if (!ALLOWED_METADATA_FIELDS.contains(metadataField)) return null;
			// Auto-apply correct suffix for known metadata fields (only if not already specified)
			if (!numericSort && !lengthSort) {
				if (METADATA_LEN_FIELDS.contains(metadataField)) {
					lengthSort = true;
				} else if (METADATA_NUM_FIELDS.contains(metadataField)) {
					numericSort = true;
				}
			}
		}

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
	 * A sort on the value of a tag in the tags array, e.g. "tags->plugin/progress:num".
	 *
	 * @param function the SQL function: tag_value, tag_value_num or tag_value_dur
	 * @param tag the tag prefix whose first sub-tag is the value
	 */
	public record TagValueSort(String function, String tag) {
		/**
		 * Parses "tags->{tag}", "tags->{tag}:num" or "tags->{tag}:dur". The "tags->" prefix is optional.
		 *
		 * @return the parsed sort, or null if the property is not a valid tag value sort
		 */
		public static TagValueSort parse(String property) {
			if (property == null) return null;
			if (property.startsWith("tags->")) property = property.substring("tags->".length());
			var function = "tag_value";
			if (property.endsWith(":num")) {
				function = "tag_value_num";
				property = property.substring(0, property.length() - ":num".length());
			} else if (property.endsWith(":dur")) {
				function = "tag_value_dur";
				property = property.substring(0, property.length() - ":dur".length());
			}
			if (!property.matches(Tag.REGEX)) return null;
			return new TagValueSort(function, property);
		}
	}

	/**
	 * Creates a sort expression on the value of a tag in the tags array.
	 * For example, "tags->plugin/duration:dur" sorts on 625 seconds for the tag "plugin/duration/pt10m25s",
	 * and "tags->plugin/progress:num" sorts numerically on 37 for the tag "plugin/progress/37/100".
	 * Only the first sub-tag after the prefix is used, and only the first tag with a value is considered.
	 * Missing values sort as 0 for numeric and duration, '' for string.
	 *
	 * @param root the query root
	 * @param cb the criteria builder
	 * @param property the sort property (e.g., "tags->plugin/progress:num")
	 * @return the sort expression, or null if the property is not a valid tag value sort
	 */
	public static Expression<?> createTagValueSortExpression(Root<?> root, CriteriaBuilder cb, String property) {
		var sort = TagValueSort.parse(property);
		if (sort == null) return null;
		if ("tag_value".equals(sort.function())) return cb.function(sort.function(), String.class, root.get("tags"), cb.literal(sort.tag()));
		return cb.function(sort.function(), Double.class, root.get("tags"), cb.literal(sort.tag()));
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

	/**
	 * Creates a sort expression for the tag field with binary collation (COLLATE "C").
	 * This ensures ASCII ordering where '+' comes before '_'.
	 */
	public static Expression<String> createTagSortExpression(Root<?> root, CriteriaBuilder cb) {
		return cb.function("collate_c", String.class, root.get("tag"));
	}
}
