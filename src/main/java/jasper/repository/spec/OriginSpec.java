package jasper.repository.spec;

import jasper.domain.proj.HasOrigin;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.data.jpa.domain.Specification.unrestricted;

public class OriginSpec {

	public static <T extends HasOrigin> Specification<T> isOrigin(String origin) {
		if (origin.equals("@*")) return any();
		if (isBlank(origin) || origin.equals("@")) {
			return (root, query, cb) ->
				cb.equal(
					root.get("origin"),
					"");
		} else if (origin.endsWith(".*")) {
			var match = origin.substring(0, origin.length() - 1) + "%";
			var rootOrigin = origin.substring(0, origin.length() - 2);
			return (root, query, cb) ->
				cb.or(
					cb.equal(
						root.get("origin"),
						rootOrigin),
					cb.like(
						root.get("origin"),
						match));
		} else {
			return (root, query, cb) ->
				cb.equal(
					root.get("origin"),
					origin);
		}
	}

	public static <T extends HasOrigin> Specification<T> isUnderOrigin(String origin) {
		if (isBlank(origin) || origin.equals("@") || origin.equals("@*")) return any();
		return (root, query, cb) ->
			cb.or(
				cb.equal(
					root.get("origin"),
					origin),
				cb.like(
					root.get("origin"),
					origin + ".%"));
	}

	public static <T extends HasOrigin> Specification<T> isAnyOrigin(List<String> origins) {
		if (origins == null || origins.isEmpty()) return unrestricted();
		for (var o : origins) if (o.equals("@*")) return any();
		if (origins.size() == 1) return isOrigin(origins.get(0));
		return (root, query, cb) ->
			root.get("origin")
				.in(origins);
	}

	public static <T extends HasOrigin> Specification<T> isNesting(int nesting) {
		return (root, query, cb) ->
			cb.equal(
				root.get("nesting"),
				nesting);
	}

	public static <T extends HasOrigin> Specification<T> any() {
		return (root, query, cb) -> cb.conjunction();
	}

	public static <T extends HasOrigin> Specification<T> none() {
		return (root, query, cb) -> cb.disjunction();
	}
}
