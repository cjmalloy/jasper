package jasper.repository.spec;

import jasper.domain.proj.HasOrigin;
import org.springframework.data.jpa.domain.Specification;

public class OriginSpec {

	public static <T extends HasOrigin> Specification<T> isOrigin(String origin) {
		if (origin.equals("@*")) return null;
		return (root, query, cb) ->
			cb.equal(
				root.get("origin"),
				origin);
	}

	public static <T extends HasOrigin> Specification<T> any() {
		return (root, query, cb) ->
			cb.conjunction();
	}

	public static <T extends HasOrigin> Specification<T> none() {
		return (root, query, cb) -> cb.disjunction();
	}
}
