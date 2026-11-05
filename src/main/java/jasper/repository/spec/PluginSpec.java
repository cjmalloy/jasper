package jasper.repository.spec;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jasper.domain.Plugin;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;

import static jasper.repository.spec.SortSpec.createJsonbOrder;
import static jasper.repository.spec.SortSpec.createOriginNestingExpression;
import static jasper.repository.spec.SortSpec.createTagLevelsExpression;
import static jasper.repository.spec.SortSpec.isJsonbSortProperty;

public class PluginSpec {

	/**
	 * The JSONB columns Plugins can be sorted and filtered (configBefore / configAfter) by.
	 */
	public static final String[] JSONB_FIELDS = { "config", "defaults", "schema" };

	/**
	 * Creates a Specification with sorting applied based on the PageRequest's sort orders.
	 * JSONB field sort columns are rewritten as JPA Specification orderBy clauses.
	 * Supports config->, defaults->, schema->, origin:len, and tag:len sorting.
	 * Missing JSONB values sort first ascending and last descending.
	 *
	 * @param spec the base specification to add sorting to
	 * @param pageable the page request containing sort orders
	 * @return a new Specification with sorting applied for all fields
	 */
	public static Specification<Plugin> sort(Specification<Plugin> spec, Pageable pageable) {
		if (pageable == null || pageable.getSort().isUnsorted()) {
			return spec;
		}
		var orders = pageable.getSort().toList();
		return spec.and((root, query, cb) -> {
			if (query.getResultType() == Long.class || query.getResultType() == long.class) {
				return null; // Don't apply ordering to count queries
			}
			var jpaOrders = new ArrayList<Order>();
			for (Sort.Order order : orders) {
				var property = order.getProperty();
				var ascending = order.isAscending();
				Expression<?> expr;
				if (isJsonbSortProperty(property, JSONB_FIELDS)) {
					var jsonbOrder = createJsonbOrder(root, cb, property, ascending, JSONB_FIELDS);
					if (jsonbOrder != null) jpaOrders.add(jsonbOrder);
					continue;
				}
				if (property.equals("origin:len")) {
					expr = createOriginNestingExpression(root, cb);
				} else if (property.equals("tag:len")) {
					expr = createTagLevelsExpression(root, cb);
				} else {
					expr = root.get(property);
				}
				if (expr != null) jpaOrders.add(ascending ? cb.asc(expr) : cb.desc(expr));
			}
			if (!jpaOrders.isEmpty()) query.orderBy(jpaOrders);
			return null;
		});
	}
}
