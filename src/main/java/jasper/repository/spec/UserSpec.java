package jasper.repository.spec;

import jakarta.persistence.criteria.Expression;
import jasper.domain.User;
import jasper.domain.User_;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.springframework.data.jpa.domain.Specification.unrestricted;

public class UserSpec {

	private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("\\[(\\d+)]");

	public static Specification<User> hasAuthorizedKeys() {
		return (root, query, cb) ->
			cb.isNotNull(
				root.get("authorizedKeys"));
	}

	/**
	 * Creates a Specification with sorting applied based on the PageRequest's sort orders.
	 * JSONB field sort columns are rewritten as JPA Specification orderBy clauses.
	 *
	 * @param spec the base specification to add sorting to
	 * @param pageable the page request containing sort orders
	 * @return a new Specification with sorting applied for JSONB fields
	 */
	public static Specification<User> applySortingSpec(Specification<User> spec, Pageable pageable) {
		if (pageable == null || pageable.getSort().isUnsorted()) {
			return spec;
		}
		var result = spec;
		for (Sort.Order order : pageable.getSort()) {
			var property = order.getProperty();
			var ascending = order.isAscending();
			if (property != null && property.startsWith("external->")) {
				var jsonbSpec = createJsonbSortSpec(property, ascending);
				if (jsonbSpec != null) {
					result = result.and(jsonbSpec);
				}
			}
		}
		return result;
	}

	/**
	 * Creates a Specification that applies ordering for a JSONB sort property.
	 *
	 * @param property the JSONB property path (e.g., "external->ids[0]" or "external->count:num")
	 * @param ascending true for ascending order, false for descending
	 * @return a Specification that applies the ordering, or null if invalid
	 */
	private static Specification<User> createJsonbSortSpec(String property, boolean ascending) {
		var parts = property.split("->");
		if (parts.length < 2) {
			return null;
		}
		// Handle "external->{field}" pattern
		if ("external".equals(parts[0])) {
			var fieldPath = Arrays.copyOfRange(parts, 1, parts.length);
			return orderByExternalField(List.of(fieldPath), ascending);
		}
		return null;
	}

	/**
	 * Creates a specification that adds ordering based on a field within User's external data.
	 * Supports array access with [index] notation (e.g., "ids[0]").
	 * Append ":num" to the last field for numeric sorting (e.g., "count:num").
	 *
	 * @param fieldPath the path to the field within the external data
	 * @param ascending true for ascending order, false for descending
	 * @return a specification that applies the ordering
	 */
	public static Specification<User> orderByExternalField(List<String> fieldPath, boolean ascending) {
		if (fieldPath == null || fieldPath.isEmpty()) return unrestricted();
		return (root, query, cb) -> {
			// Check if numeric sorting is requested on the last field
			var lastField = fieldPath.get(fieldPath.size() - 1);
			var numericSort = lastField.endsWith(":num");
			if (numericSort) {
				lastField = lastField.substring(0, lastField.length() - 4);
			}
			Expression<?> expr = root.get(User_.external);
			for (int i = 0; i < fieldPath.size(); i++) {
				var field = (i == fieldPath.size() - 1) ? lastField : fieldPath.get(i);
				// Check for array index notation like "ids[0]"
				var matcher = ARRAY_INDEX_PATTERN.matcher(field);
				if (matcher.find()) {
					var fieldName = field.substring(0, matcher.start());
					var index = Integer.parseInt(matcher.group(1));
					if (!fieldName.isEmpty()) {
						expr = cb.function("jsonb_object_field", Object.class,
							expr,
							cb.literal(fieldName));
					}
					expr = cb.function("jsonb_array_element_text", String.class,
						expr,
						cb.literal(index));
				} else {
					expr = cb.function("jsonb_object_field_text", String.class,
						expr,
						cb.literal(field));
				}
			}
			// Cast to numeric if requested
			if (numericSort) {
				expr = cb.function("cast_to_numeric", Double.class, expr);
			}
			if (ascending) {
				query.orderBy(cb.asc(expr));
			} else {
				query.orderBy(cb.desc(expr));
			}
			return null;
		};
	}
}
