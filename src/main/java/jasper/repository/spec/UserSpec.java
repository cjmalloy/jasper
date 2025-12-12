package jasper.repository.spec;

import jakarta.persistence.criteria.Expression;
import jasper.domain.User;
import jasper.domain.User_;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static jasper.repository.spec.SortSpec.*;

public class UserSpec {

	private static final List<String> JSONB_PREFIXES = List.of("external");

	public static Specification<User> hasAuthorizedKeys() {
		return (root, query, cb) ->
			cb.isNotNull(
				root.get("authorizedKeys"));
	}

	/**
	 * Creates a Specification with sorting applied based on the PageRequest's sort orders.
	 * JSONB field sort columns are rewritten as JPA Specification orderBy clauses.
	 * Uses COALESCE to handle nulls (0 for numeric/length, '' for string).
	 *
	 * @param spec the base specification to add sorting to
	 * @param pageable the page request containing sort orders
	 * @return a new Specification with sorting applied for all fields
	 */
	public static Specification<User> sort(Specification<User> spec, Pageable pageable) {
		if (pageable == null || pageable.getSort().isUnsorted()) {
			return spec;
		}
		var orders = pageable.getSort().toList();
		return spec.and((root, query, cb) -> {
			if (query.getResultType() == Long.class || query.getResultType() == long.class) {
				return null; // Don't apply ordering to count queries
			}
			var jpaOrders = new java.util.ArrayList<jakarta.persistence.criteria.Order>();
			for (Sort.Order order : orders) {
				var property = order.getProperty();
				var ascending = order.isAscending();
				if (property == null) continue;

				Expression<?> expr;
				if (isJsonbSortProperty(property, "external")) {
					expr = createJsonbSortExpression(root, cb, property, JSONB_PREFIXES);
				} else if (property.equals("origin:len")) {
					expr = createOriginNestingExpression(root, cb);
				} else if (property.equals("tag:len")) {
					expr = createTagLevelsExpression(root, cb);
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
			return null;
		});
	}
}
