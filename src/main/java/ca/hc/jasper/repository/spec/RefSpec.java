package ca.hc.jasper.repository.spec;

import static ca.hc.jasper.repository.spec.TagSpec.literal;

import java.util.List;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.domain.Ref_;
import org.springframework.data.jpa.domain.Specification;

public class RefSpec {

	public static Specification<Ref> hasTag(String tag) {
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists", Boolean.class,
				root.get(Ref_.tags),
				cb.literal(tag)));
	}

	public static Specification<Ref> hasAnyTag(List<String> tags) {
		if (tags == null || tags.isEmpty()) return null;
		if (tags.size() == 1) return hasTag(tags.get(0));
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists_any", Boolean.class,
				root.get(Ref_.tags),
				literal(cb, tags)));
	}

	public static Specification<Ref> hasAllTags(List<String> tags) {
		if (tags == null || tags.isEmpty()) return null;
		if (tags.size() == 1) return hasTag(tags.get(0));
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists_all", Boolean.class,
				root.get(Ref_.tags),
				literal(cb, tags)));
	}
}
