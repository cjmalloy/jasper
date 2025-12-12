package jasper.repository.spec;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class JsonSpec {

	/**
	 * Creates a PageRequest with JSONB sort columns removed from the sort orders.
	 * Non-JSONB sort columns are preserved. This should be used in conjunction with
	 * applySortingSpec() which handles the JSONB sorting via Specifications.
	 *
	 * @param pageable the original page request
	 * @return a new PageRequest with JSONB sort columns removed
	 */
	public static PageRequest clearJsonbSort(Pageable pageable) {
		if (pageable == null) {
			return PageRequest.of(0, 20);
		}
		if (pageable.getSort().isUnsorted()) {
			return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
		}
		var validOrders = pageable.getSort().stream()
			.filter(order -> !isJsonbSortProperty(order.getProperty()))
			.toList();
		if (validOrders.isEmpty()) {
			return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
		}
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(validOrders));
	}

	/**
	 * Checks if a sort property targets a JSONB field.
	 * JSONB properties include patterns like:
	 * - "metadata->plugins->{tag}" or "metadata->{field}" (Ref)
	 * - "plugins->{tag}->{field}" (Ref)
	 * - "external->{field}" (User)
	 * - "config->{field}" (Ext)
	 *
	 * @param property the sort property name
	 * @return true if this is a JSONB sort property
	 */
	public static boolean isJsonbSortProperty(String property) {
		if (property == null) return false;
		return property.startsWith("metadata->") ||
			property.startsWith("plugins->") ||
			property.startsWith("external->") ||
			property.startsWith("config->");
	}
}
