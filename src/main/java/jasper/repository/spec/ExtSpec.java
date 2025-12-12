package jasper.repository.spec;

import jakarta.persistence.criteria.Expression;
import jasper.domain.Ext;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;

import static org.springframework.data.jpa.domain.Specification.unrestricted;

public class ExtSpec {

	/**
	 * Creates a Specification with sorting applied based on the PageRequest's sort orders.
	 * JSONB field sort columns are rewritten as JPA Specification orderBy clauses.
	 *
	 * @param spec the base specification to add sorting to
	 * @param pageable the page request containing sort orders
	 * @return a new Specification with sorting applied for JSONB fields
	 */
	public static Specification<Ext> applySortingSpec(Specification<Ext> spec, Pageable pageable) {
		if (pageable == null || pageable.getSort().isUnsorted()) {
			return spec;
		}
		var result = spec;
		for (Sort.Order order : pageable.getSort()) {
			var property = order.getProperty();
			var ascending = order.isAscending();
			if (TagSpec.isJsonbSortProperty(property)) {
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
	 * @param property the JSONB property path (e.g., "config->value" or "config->count:num")
	 * @param ascending true for ascending order, false for descending
	 * @return a Specification that applies the ordering, or null if invalid
	 */
	private static Specification<Ext> createJsonbSortSpec(String property, boolean ascending) {
		var parts = property.split("->");
		if (parts.length < 2) {
			return null;
		}
		// Handle "config->{field}" pattern
		if ("config".equals(parts[0])) {
			var fieldPath = Arrays.copyOfRange(parts, 1, parts.length);
			return orderByConfigField(List.of(fieldPath), ascending);
		}
		return null;
	}

	/**
	 * Creates a specification that adds ordering based on a field within Ext's config data.
	 * Append ":num" to the last field for numeric sorting (e.g., "count:num").
	 *
	 * @param fieldPath the path to the field within the config data
	 * @param ascending true for ascending order, false for descending
	 * @return a specification that applies the ordering
	 */
	public static Specification<Ext> orderByConfigField(List<String> fieldPath, boolean ascending) {
		if (fieldPath == null || fieldPath.isEmpty()) return unrestricted();
		return (root, query, cb) -> {
			// Check if numeric sorting is requested
			var lastField = fieldPath.get(fieldPath.size() - 1);
			var numericSort = lastField.endsWith(":num");
			if (numericSort) {
				lastField = lastField.substring(0, lastField.length() - 4);
			}
			Expression<?> expr = root.get("config");
			for (int i = 0; i < fieldPath.size() - 1; i++) {
				expr = cb.function("jsonb_object_field", Object.class,
					expr,
					cb.literal(fieldPath.get(i)));
			}
			// Get the final field as text
			expr = cb.function("jsonb_object_field_text", String.class,
				expr,
				cb.literal(lastField));
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
