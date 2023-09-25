package jasper.repository.spec;

import jasper.domain.proj.HasOrigin;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public class OriginSpec {

	public static <T extends HasOrigin> Specification<T> isOrigin(String origin) {
		if (origin.equals("@*")) return any();
		return (root, query, cb) ->
			cb.equal(
				root.get("origin"),
				origin.equals("@") ? "" : origin);
	}

	public static <T extends HasOrigin> Specification<T> isAnyOrigin(List<String> origins) {
		if (origins == null || origins.isEmpty()) return null;
		for (var o : origins) if (o.equals("@*")) return any();
		if (origins.size() == 1) return isOrigin(origins.get(0));
		return (root, query, cb) ->
			root.get("origin")
				.in(origins);
	}

	public static <T extends HasOrigin> Specification<T> any() {
		return (root, query, cb) ->
			cb.conjunction();
	}

	public static <T extends HasOrigin> Specification<T> none() {
		return (root, query, cb) -> cb.disjunction();
	}
}
