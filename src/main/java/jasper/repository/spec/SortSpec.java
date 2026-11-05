package jasper.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jasper.domain.Plugin;
import jasper.domain.proj.Tag;
import jasper.errors.InvalidQueryException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static jasper.config.SQLiteConfig.isoDurationSeconds;
import static jasper.domain.Metadata.timestamp;
import static java.util.Arrays.stream;

/**
 * Sort and filter columns. Each column renders one SQL expression for both sorting and filtering, so one index serves both.
 * Used by entity-specific Spec classes (RefSpec, ExtSpec, UserSpec, PluginSpec, TemplateSpec).
 */
public class SortSpec {

	private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("\\[(\\d+)]");
	public static final String FIELD_REGEX = "(?:[A-Za-z0-9_$-]+(?:\\[[0-9]{1,9}\\])?|\\[[0-9]{1,9}\\])";
	/**
	 * A plugin sort column, e.g. "plugins->plugin/test->size:num".
	 */
	public static final String PLUGIN_COLUMN_REGEX = "plugins->" + Plugin.REGEX + "(?:->" + FIELD_REGEX + ")*(?::num|:len)?";

	/**
	 * A sort or filter column, so both render the same SQL expression and one index serves both.
	 *
	 * @param expression the value: an Instant for dates, an Integer for lengths, a Double for numbers, otherwise a String
	 * @param nullable missing values are null, so they sort first ascending and last descending,
	 *                 so one "(expression) NULLS FIRST" index serves both directions.
	 *                 This is the default null order on SQLite, so no NULLS clause is rendered there.
	 * @param present only rows with a value, or null if every row has a value
	 */
	public record Column(Expression<?> expression, boolean nullable, Predicate present) {
		public Column(Expression<?> expression, boolean nullable) {
			this(expression, nullable, null);
		}

		public Order order(CriteriaBuilder cb, boolean ascending) {
			if (nullable) return ascending ? cb.asc(expression, Nulls.FIRST) : cb.desc(expression, Nulls.LAST);
			return ascending ? cb.asc(expression) : cb.desc(expression);
		}

		/**
		 * Compares the column with a filter value. Missing values and values of the wrong type never match.
		 */
		@SuppressWarnings("unchecked")
		public Predicate filter(CriteriaBuilder cb, Filter filter) {
			Predicate result;
			if (filter.value() instanceof Instant date) {
				Expression<Instant> expr = (Expression<Instant>) expression;
				result = switch (filter.op()) {
					case BEFORE -> cb.lessThan(expr, date);
					case AFTER -> cb.greaterThan(expr, date);
					case EQUALS -> cb.equal(expr, date);
				};
			} else if (filter.value() instanceof String text) {
				Expression<String> expr = (Expression<String>) expression;
				result = switch (filter.op()) {
					case BEFORE -> cb.lessThan(expr, text);
					case AFTER -> cb.greaterThan(expr, text);
					case EQUALS -> cb.equal(expr, text);
				};
			} else if (filter.value() instanceof Integer length) {
				Expression<Integer> expr = (Expression<Integer>) expression;
				result = switch (filter.op()) {
					case BEFORE -> cb.lessThan(expr, cb.literal(length));
					case AFTER -> cb.greaterThan(expr, cb.literal(length));
					case EQUALS -> cb.equal(expr, cb.literal(length));
				};
			} else {
				Expression<Double> expr = (Expression<Double>) expression;
				var number = cb.function("cast_to_numeric", Double.class, cb.literal(filter.value()));
				result = switch (filter.op()) {
					case BEFORE -> cb.lessThan(expr, number);
					case AFTER -> cb.greaterThan(expr, number);
					case EQUALS -> cb.equal(expr, number);
				};
			}
			return present == null ? result : cb.and(present, result);
		}
	}

	/**
	 * A filter on a sort column, e.g. "modified/2024-01-01T00:00:00Z" or "plugins->plugin/test->size:num/10".
	 * Columns on Refs: "modified", "published", "created", "metadata->newResponse", "metadata->newReaction",
	 * "metadata->expandedTags", "metadata->responses", "metadata->internalResponses", response counts,
	 * vote sorts, plugin data, tag values, "tags:len", "sources:len", "alternateUrls:len" and "origin:len".
	 * Columns on Exts, Users, Plugins and Templates: "modified", "origin:len", "tag:len" and JSONB values.
	 * The value is always after the last "/", so it cannot contain "/".
	 * Values cannot contain "->" so a missing value is not mistaken for a field.
	 * Values cannot contain "," since Spring splits a single list parameter on commas.
	 * The column decides the type of the value:
	 * <ul>
	 *     <li>"modified", "published" and "created": an ISO instant</li>
	 *     <li>"metadata->newResponse" and "metadata->newReaction": an ISO instant, compared with the timestamp text metadata stores</li>
	 *     <li>":len", "metadata->expandedTags", "metadata->responses" and "metadata->internalResponses": a length</li>
	 *     <li>":dur": an ISO duration</li>
	 *     <li>":num", vote sorts and response counts ("metadata->plugins->{plugin}"): a number</li>
	 *     <li>otherwise text</li>
	 * </ul>
	 *
	 * @param column the sort column, e.g. "plugins->plugin/test->size:num"
	 * @param value an Instant, String, Integer, or a BigDecimal for numbers and durations in seconds
	 */
	public record Filter(String column, Op op, Object value) {
		public enum Op { BEFORE, AFTER, EQUALS }
		/**
		 * The sort columns of Refs, Exts, Users, Plugins and Templates that can be filtered.
		 */
		public static final String COLUMN_REGEX = "(?:modified|published|created|origin:len|tag:len|(?:tags|sources|alternateUrls):len"
			+ "|metadata->(?:newResponse|newReaction)"
			+ "|metadata->(?:expandedTags|responses|internalResponses)(?::len)?"
			+ "|metadata->(?:plugins|remotePlugins)->" + Plugin.REGEX + "(?::num)?"
			+ "|plugins->plugin/user/vote:(?:top|score|decay)"
			+ "|" + PLUGIN_COLUMN_REGEX
			+ "|tags->" + Tag.REGEX + "(?::num|:dur)?"
			+ "|(?:config|defaults|schema|external)(?:->" + FIELD_REGEX + ")+(?::num|:len)?)";
		public static final String VALUE_REGEX = "(?:(?!->)[^/,])+";
		public static final String REGEX = COLUMN_REGEX + "/" + VALUE_REGEX;
		public static final int LEN = 1024;
		private static final String NUM_REGEX = "-?[0-9]{1,30}([.][0-9]{1,30})?";

		/**
		 * @return the parsed filter, or null if the value is not valid for the column
		 */
		public static Filter parse(String filter, Op op) {
			if (filter == null || !filter.matches(REGEX)) return null;
			var i = filter.lastIndexOf('/');
			var column = filter.substring(0, i);
			var value = filter.substring(i + 1);
			try {
				if (column.equals("modified") || column.equals("published") || column.equals("created")) {
					return new Filter(column, op, Instant.parse(value));
				}
				if (column.equals("metadata->newResponse") || column.equals("metadata->newReaction")) {
					return new Filter(column, op, timestamp(Instant.parse(value)));
				}
			} catch (DateTimeParseException e) {
				return null;
			}
			if (column.endsWith(":len") || column.matches("metadata->(?:expandedTags|responses|internalResponses)")) {
				if (!value.matches("[0-9]{1,9}")) return null;
				return new Filter(column, op, Integer.valueOf(value));
			}
			if (column.endsWith(":dur")) {
				var seconds = isoDurationSeconds(value);
				if (seconds == null) return null;
				return new Filter(column, op, BigDecimal.valueOf(seconds));
			}
			if (column.endsWith(":num") || column.matches("plugins->plugin/user/vote:(?:top|score|decay)|metadata->(?:plugins|remotePlugins)->.*")) {
				if (!value.matches(NUM_REGEX)) return null;
				return new Filter(column, op, new BigDecimal(value));
			}
			return new Filter(column, op, value);
		}

		/**
		 * Parses the filterBefore, filterAfter and filterEquals parameters.
		 *
		 * @throws InvalidQueryException if a value is not valid for its column
		 */
		public static List<Filter> parse(List<String> before, List<String> after, List<String> equals) {
			var result = new ArrayList<Filter>();
			for (var op : Op.values()) {
				var filters = switch (op) {
					case BEFORE -> before;
					case AFTER -> after;
					case EQUALS -> equals;
				};
				if (filters == null) continue;
				for (var f : filters) {
					var filter = parse(f, op);
					if (filter == null) throw new InvalidQueryException(f, "expected {sort column}/{value} with a value of the column type");
					result.add(filter);
				}
			}
			return result;
		}
	}

	/**
	 * The column of a JSONB value, e.g. "config->field->subfield:num".
	 * Supports ":num" suffix for numeric values, ":len" suffix for array lengths and "[index]" for array elements.
	 * Missing values (or values of the wrong type for :num or :len) are null.
	 *
	 * @param property the sort column
	 * @param prefixes the JSONB columns that may be used, e.g. "config", "defaults", "schema"
	 * @return the column, or null if property doesn't match allowed prefixes
	 */
	public static Column jsonbColumn(Root<?> root, CriteriaBuilder cb, String property, String... prefixes) {
		var numericSort = property.endsWith(":num");
		var lengthSort = property.endsWith(":len");
		if (property.contains(":")) property = property.substring(0, property.lastIndexOf(":"));
		var parts = property.split("->");
		if (parts.length < 2) return null;
		var jsonbFieldName = parts[0];
		if (stream(prefixes).noneMatch(jsonbFieldName::equals)) return null;

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
				if (i < parts.length - 1) {
					// Intermediate element - keep as JSONB for further traversal
					expr = cb.function("jsonb_array_element", Object.class, expr, cb.literal(index));
				} else if (lengthSort) {
					// Last element with length sort - get as JSONB and apply jsonb_array_length
					expr = cb.function("jsonb_array_element", Object.class, expr, cb.literal(index));
					return new Column(cb.function("safe_jsonb_array_length", Integer.class, expr), true);
				} else if (numericSort) {
					// Last element with numeric sort - get as JSONB to check for a JSON number
					expr = cb.function("jsonb_array_element", Object.class, expr, cb.literal(index));
				} else {
					// Last element - get as text
					expr = cb.function("jsonb_array_element_text", String.class, expr, cb.literal(index));
				}
			} else if (i == parts.length - 1 && lengthSort) {
				// Last field with length sort - get as JSONB and apply jsonb_array_length
				expr = cb.function("jsonb_object_field", Object.class, expr, cb.literal(field));
				return new Column(cb.function("safe_jsonb_array_length", Integer.class, expr), true);
			} else if (i == parts.length - 1 && numericSort) {
				// Last field with numeric sort - get as JSONB to check for a JSON number
				expr = cb.function("jsonb_object_field", Object.class, expr, cb.literal(field));
			} else if (i == parts.length - 1) {
				// Last field - get as text
				expr = cb.function("jsonb_object_field_text", String.class, expr, cb.literal(field));
			} else {
				// Intermediate field - get as JSONB object
				expr = cb.function("jsonb_object_field", Object.class, expr, cb.literal(field));
			}
		}
		if (numericSort) return new Column(cb.function("safe_numeric", Double.class, expr), true);
		return new Column(expr, true);
	}

	/**
	 * The column of an Ext, User, Plugin or Template: "modified", "origin:len", "tag:len" or a JSONB value.
	 *
	 * @param roots the JSONB columns of the entity, e.g. "config"
	 * @return the column, or null if this is not a computed column
	 */
	public static Column tagColumn(Root<?> root, CriteriaBuilder cb, String property, String... roots) {
		if (isJsonbSortProperty(property, roots)) return jsonbColumn(root, cb, property, roots);
		return switch (property) {
			case "modified" -> new Column(root.get("modified"), false);
			case "origin:len" -> new Column(cb.function("origin_nesting", Integer.class, root.get("origin")), false);
			case "tag:len" -> new Column(cb.function("tag_levels", Integer.class, root.get("tag")), false);
			default -> null;
		};
	}

	/**
	 * Orders an Ext, User, Plugin or Template by its sort columns.
	 * Missing JSONB values sort first ascending and last descending.
	 *
	 * @param roots the JSONB columns of the entity, e.g. "config"
	 */
	public static <T> Specification<T> sortTags(Specification<T> spec, Pageable pageable, String... roots) {
		if (pageable == null || pageable.getSort().isUnsorted()) {
			return spec;
		}
		var orders = pageable.getSort().toList();
		return spec.and((root, query, cb) -> {
			if (query.getResultType() == Long.class || query.getResultType() == long.class) {
				return null; // Don't apply ordering to count queries
			}
			var jpaOrders = new ArrayList<Order>();
			for (var order : orders) {
				var column = tagColumn(root, cb, order.getProperty(), roots);
				if (column != null) {
					jpaOrders.add(column.order(cb, order.isAscending()));
				} else if (!isJsonbSortProperty(order.getProperty(), roots)) {
					jpaOrders.add(order.isAscending() ? cb.asc(root.get(order.getProperty())) : cb.desc(root.get(order.getProperty())));
				}
			}
			if (!jpaOrders.isEmpty()) query.orderBy(jpaOrders);
			return null;
		});
	}

	/**
	 * Filters an Ext, User, Plugin or Template on its sort columns.
	 *
	 * @param roots the JSONB columns of the entity, e.g. "config"
	 */
	public static <T> Specification<T> filterTags(Filter filter, String... roots) {
		return (root, query, cb) -> {
			var column = tagColumn(root, cb, filter.column(), roots);
			if (column == null) throw new InvalidQueryException(filter.column(), "not a sort column");
			return column.filter(cb, filter);
		};
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
	 * Checks if a property is a JSONB sort property.
	 */
	public static boolean isJsonbSortProperty(String property, String... prefixes) {
		return stream(prefixes).anyMatch(p -> property.startsWith(p + "->"));
	}

	/**
	 * Creates a sort expression for the tag field with binary collation (COLLATE "C").
	 * This ensures ASCII ordering where '+' comes before '_'.
	 */
	public static Expression<String> createTagSortExpression(Root<?> root, CriteriaBuilder cb) {
		return cb.function("collate_c", String.class, root.get("tag"));
	}
}
