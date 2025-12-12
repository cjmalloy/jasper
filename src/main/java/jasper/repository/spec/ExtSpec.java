package jasper.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jasper.domain.Ext;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public class ExtSpec {

	/**
	 * Creates a Specification with sorting applied based on the PageRequest's sort orders.
	 * JSONB field sort columns are rewritten as JPA Specification orderBy clauses.
	 *
	 * @param spec the base specification to add sorting to
	 * @param pageable the page request containing sort orders
	 * @return a new Specification with sorting applied for all fields
	 */
	public static Specification<Ext> applySortingSpec(Specification<Ext> spec, Pageable pageable) {
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
				boolean isJsonbField = property.startsWith("config->");
				boolean isLengthSort = property.endsWith(":len");
				if (isJsonbField) {
					expr = createJsonbSortExpression(root, cb, property);
					// Filter out records where the JSONB path returns null
					if (expr != null) {
						predicates.add(cb.isNotNull(expr));
					}
				} else if (isLengthSort) {
					// Handle origin:len for nesting level
					var fieldName = property.substring(0, property.length() - 4);
					if ("origin".equals(fieldName)) {
						expr = cb.function("origin_nesting", Integer.class, root.get(fieldName));
					} else {
						expr = root.get(property);
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
	private static Expression<?> createJsonbSortExpression(Root<Ext> root, CriteriaBuilder cb, String property) {
		var parts = property.split("->");
		if (parts.length < 2 || !"config".equals(parts[0])) {
			return null;
		}
		
		// Check if numeric or length sorting is requested
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
		
		Expression<?> expr = root.get("config");
		for (int i = 1; i < parts.length - 1; i++) {
			expr = cb.function("jsonb_object_field", Object.class,
				expr,
				cb.literal(parts[i]));
		}
		// Get the final field - as JSONB for length, as text otherwise
		if (lengthSort) {
			expr = cb.function("jsonb_object_field", Object.class,
				expr,
				cb.literal(lastField));
			expr = cb.function("jsonb_array_length", Integer.class, expr);
		} else {
			expr = cb.function("jsonb_object_field_text", String.class,
				expr,
				cb.literal(lastField));
			if (numericSort) {
				expr = cb.function("cast_to_numeric", Double.class, expr);
			}
		}
		return expr;
	}
}
