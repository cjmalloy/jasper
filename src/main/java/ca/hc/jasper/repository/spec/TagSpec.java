package ca.hc.jasper.repository.spec;

import static ca.hc.jasper.repository.spec.OriginSpec.isOrigin;

import java.util.List;

import ca.hc.jasper.domain.proj.*;
import ca.hc.jasper.repository.filter.QualifiedTag;
import org.springframework.data.jpa.domain.Specification;

public class TagSpec {

	public static <T extends IsTag> Specification<T> publicTag() {
		return (root, query, cb) ->
			cb.not(
				cb.like(
					root.get("tag"),
					"\\_%"));
	}

	public static <T extends IsTag> Specification<T> isTag(String tag) {
		return (root, query, cb) ->
			cb.equal(
				root.get("tag"),
				tag);
	}

	public static <T extends IsTag> Specification<T> isAnyTag(List<String> tags) {
		if (tags == null || tags.isEmpty()) return null;
		if (tags.size() == 1) return isTag(tags.get(0));
		return (root, query, cb) ->
			root.get("tag")
				.in(tags);
	}

	public static <T extends IsTag> Specification<T> isQualifiedTag(QualifiedTag tag) {
		if (tag.getOrigin().equals("*")) return isTag(tag.getTag());
		if (tag.getTag().equals("*")) return isOrigin(tag.getOrigin());
		return Specification
			.<T>where(isTag(tag.getTag()))
			.and(isOrigin(tag.getOrigin()));
	}

	public static <T extends IsTag> Specification<T> isAnyQualifiedTag(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		if (tags.size() == 1) return isQualifiedTag(tags.get(0));
		var spec = Specification.<T>where(null);
		for (var t : tags) {
			spec.or(isQualifiedTag(t));
		}
		return spec;
	}
}
