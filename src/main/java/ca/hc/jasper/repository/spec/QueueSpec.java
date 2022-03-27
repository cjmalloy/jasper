package ca.hc.jasper.repository.spec;

import static ca.hc.jasper.repository.spec.TagSpec.literal;

import java.util.List;

import ca.hc.jasper.domain.*;
import org.springframework.data.jpa.domain.Specification;

public class QueueSpec {

	public static Specification<Queue> publicQueue() {
		return (root, query, cb) ->
			cb.not(
				cb.like(
					root.get(Tag_.TAG),
					"\\_%"));
	}

	public static Specification<Queue> isTag(String tag) {
		return (root, query, cb) ->
			cb.equal(
				root.get(Queue_.TAG),
				tag);
	}

	public static Specification<Queue> isAny(List<String> tags) {
		if (tags == null || tags.isEmpty()) return null;
		if (tags.size() == 1) return isTag(tags.get(0));
		return (root, query, cb) ->
			root.get(Queue_.TAG)
				.in(literal(cb, tags));
	}
}
