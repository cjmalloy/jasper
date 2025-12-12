package jasper.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jasper.domain.User;
import jasper.domain.User_;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.regex.Pattern;

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
	 * @return a new Specification with sorting applied for all fields
	 */
	public static Specification<User> applySortingSpec(Specification<User> spec, Pageable pageable) {
		if (pageable == null || pageable.getSort().isUnsorted()) {
			return spec;
		}
		// Collect all sort orders to apply in a single specification
		var orders = pageable.getSort().toList();
		return spec.and((root, query, cb) -> {
			if (query.getResultType() == Long.class || query.getResultType() == long.class) {
				return null; // Don't apply ordering to count queries
			}
			var jpaOrders = new java.util.ArrayList<jakarta.persistence.criteria.Order>();
			var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
			for (Sort.Order order : orders) {
				var property = order.getProperty();
				var ascending = order.isAscending();
				if (property == null) continue;
				
				Expression<?> expr;
				boolean isJsonbField = property.startsWith("external->");
				if (isJsonbField) {
					expr = createJsonbSortExpression(root, cb, property);
					// Filter out records where the JSONB path returns null
					if (expr != null) {
						predicates.add(cb.isNotNull(expr));
					}
				} else {
					expr = root.get(property);
				}
				if (expr != null) {
					jpaOrders.add(ascending ? cb.asc(expr) : cb.desc(expr));
				}
			}
			if (!jpaOrders.isEmpty()) {
				query.orderBy(jpaOrders);
			}
			if (!predicates.isEmpty()) {
				return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
			}
			return null;
		});
	}

	/**
	 * Creates a JSONB sort expression for the given property path.
	 * Supports ":num" suffix for numeric sorting and ":len" suffix for array length sorting.
	 */
	private static Expression<?> createJsonbSortExpression(Root<User> root, CriteriaBuilder cb, String property) {
		var parts = property.split("->");
		if (parts.length < 2 || !"external".equals(parts[0])) {
			return null;
		}
		
		// Check if numeric or length sorting is requested on the last field
		var lastField = parts[parts.length - 1];
		var numericSort = lastField.endsWith(":num");
		var lengthSort = lastField.endsWith(":len");
		if (numericSort) {
			parts[parts.length - 1] = lastField.substring(0, lastField.length() - 4);
			lastField = parts[parts.length - 1];
		} else if (lengthSort) {
			parts[parts.length - 1] = lastField.substring(0, lastField.length() - 4);
			lastField = parts[parts.length - 1];
		}
		
		Expression<?> expr = root.get(User_.external);
		for (int i = 1; i < parts.length; i++) {
			var field = parts[i];
			// Check for array index notation like "ids[0]"
			var matcher = ARRAY_INDEX_PATTERN.matcher(field);
			if (matcher.find()) {
				var fieldName = field.substring(0, matcher.start());
				var indexStr = matcher.group(1);
				if (indexStr == null || !indexStr.matches("\\d+")) {
					throw new IllegalArgumentException("Invalid array index in field: '" + field + "'");
				}
				var index = Integer.parseInt(indexStr);
				if (!fieldName.isEmpty()) {
					expr = cb.function("jsonb_object_field", Object.class,
						expr,
						cb.literal(fieldName));
				}
				expr = cb.function("jsonb_array_element_text", String.class,
					expr,
					cb.literal(index));
			} else if (i == parts.length - 1 && lengthSort) {
				// Last field with length sort - get as JSONB and apply jsonb_array_length
				expr = cb.function("jsonb_object_field", Object.class,
					expr,
					cb.literal(field));
				expr = cb.function("jsonb_array_length", Integer.class, expr);
			} else {
				expr = cb.function("jsonb_object_field_text", String.class,
					expr,
					cb.literal(field));
			}
		}
		// Cast to numeric if requested (not needed for length sort as it's already an integer)
		if (numericSort && !lengthSort) {
			expr = cb.function("cast_to_numeric", Double.class, expr);
		}
		return expr;
	}
}
