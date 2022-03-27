package ca.hc.jasper.repository.spec;

import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;

import ca.hc.jasper.domain.*;
import org.springframework.data.jpa.domain.Specification;

public class TagSpec {

	public static Specification<Tag> publicTag() {
		return (root, query, cb) ->
			cb.not(
				cb.like(
					root.get(Tag_.TAG),
					"\\_%"));
	}

	public static Specification<Tag> isTag(String tag) {
		return (root, query, cb) ->
			cb.equal(
				root.get(Tag_.TAG),
				tag);
	}

	public static Specification<Tag> isAny(List<String> tags) {
		if (tags == null || tags.isEmpty()) return null;
		if (tags.size() == 1) return isTag(tags.get(0));
		return (root, query, cb) ->
			root.get(Tag_.TAG)
				.in(literal(cb, tags));
	}

	public static Expression<String[]> literal(CriteriaBuilder cb, List<String> tags) {
		return cb.function("string_to_array", String[].class,
			cb.literal(String.join(",", tags)),
			cb.literal(","));
	}
}
