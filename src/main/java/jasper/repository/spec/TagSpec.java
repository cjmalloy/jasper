package jasper.repository.spec;

import jasper.domain.proj.Tag;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public class TagSpec {

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

	public static <T extends Tag> Specification<T> isAnyTag(List<String> tags) {
		if (tags == null || tags.isEmpty()) return null;
		if (tags.size() == 1) return isTag(tags.get(0));
		return (root, query, cb) ->
			root.get("tag")
				.in(tags);
	}

	public static <T extends Tag> Specification<T> isAnyQualifiedTag(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		var spec = Specification.<T>where(null);
		for (var t : tags) {
			spec = spec.or(t.spec());
		}
		return spec;
	}

	public static <T extends Tag> Specification<T> isAllQualifiedTag(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		var spec = Specification.<T>where(null);
		for (var t : tags) {
			spec = spec.and(t.spec());
		}
		return spec;
	}
}
