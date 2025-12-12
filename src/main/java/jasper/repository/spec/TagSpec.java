package jasper.repository.spec;

import jasper.domain.proj.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static jasper.domain.proj.Tag.isPublicTag;
import static jasper.domain.proj.Tag.publicTag;
import static org.springframework.data.jpa.domain.Specification.unrestricted;

public class TagSpec {

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

	public static <T extends Tag> Specification<T> searchTagOrName(String search) {
		var searchLower = search.toLowerCase();
		return (root, query, cb) ->
			cb.or(
				cb.like(cb.lower(root.get("tag")), cb.literal("%" + searchLower + "%")),
				cb.like(cb.lower(root.get("name")), cb.literal("%" + searchLower + "%"))
			);
	}

	public static <T extends Tag> Specification<T> notPrivateTag() {
		return (root, query, cb) ->
			cb.not(
				cb.like(
					root.get("tag"),
					"\\_%", '\\'));
	}

	public static <T extends Tag> Specification<T> isTag(String tag) {
		return (root, query, cb) ->
			cb.or(
				cb.equal(
					root.get("tag"),
					tag),
				cb.like(
					root.get("tag"),
					tag + "/%"));
	}

	public static <T extends Tag> Specification<T> isDownwardTag(String tag) {
		if (isPublicTag(tag)) {
			return (root, query, cb) ->
				cb.or(
					cb.equal(
						root.get("tag"),
						tag),
					cb.like(
						root.get("tag"),
						tag + "/%"));
		} else if (tag.startsWith("_")) {
			return (root, query, cb) ->
				cb.or(
					cb.equal(
						root.get("tag"),
						tag),
					cb.equal(
						root.get("tag"),
						"+" + publicTag(tag)),
					cb.equal(
						root.get("tag"),
						publicTag(tag)),
					cb.like(
						root.get("tag"),
						tag + "/%"),
					cb.like(
						root.get("tag"),
						"+" + publicTag(tag) + "/%"),
					cb.like(
						root.get("tag"),
						publicTag(tag) + "/%"));
		} else {
			// Protected tag
			return (root, query, cb) ->
				cb.or(
					cb.equal(
						root.get("tag"),
						tag),
					cb.equal(
						root.get("tag"),
						publicTag(tag)),
					cb.like(
						root.get("tag"),
						tag + "/%"),
					cb.like(
						root.get("tag"),
						publicTag(tag) + "/%"));
		}
	}

	public static <T extends Tag> Specification<T> isLevel(int level) {
		return (root, query, cb) ->
			cb.equal(
				root.get("levels"),
				level);
	}

	public static <T extends Tag> Specification<T> tagEndsWith(String tag) {
		return (root, query, cb) ->
			cb.or(
				cb.equal(
					root.get("tag"),
					tag),
				cb.like(
					root.get("tag"),
					"%/" + tag));
	}

	public static <T extends Tag> Specification<T> isAnyQualifiedTag(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return unrestricted();
		var spec = Specification.<T>unrestricted();
		for (var t : tags) {
			spec = spec.or(t.spec());
		}
		return spec;
	}

	public static <T extends Tag> Specification<T> isAllQualifiedTag(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		var spec = Specification.<T>unrestricted();
		for (var t : tags) {
			spec = spec.and(t.spec());
		}
		return spec;
	}
}
