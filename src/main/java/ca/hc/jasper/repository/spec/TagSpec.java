package ca.hc.jasper.repository.spec;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

public class TagSpec {

	public static <T> Specification<T> publicTag() {
		return (root, query, cb) ->
			cb.not(
				cb.like(
					root.get("tag"),
					"\\_%"));
	}

	public static <T> Specification<T> isTag(String tag) {
		return (root, query, cb) ->
			cb.equal(
				root.get("tag"),
				tag);
	}

	public static <T> Specification<T> isAny(List<String> tags) {
		if (tags == null || tags.isEmpty()) return null;
		if (tags.size() == 1) return isTag(tags.get(0));
		return (root, query, cb) ->
			root.get("tag")
				.in(tags);
	}
}
