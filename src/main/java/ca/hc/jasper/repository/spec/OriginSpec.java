package ca.hc.jasper.repository.spec;

import java.util.List;

import ca.hc.jasper.domain.proj.HasOrigin;
import org.springframework.data.jpa.domain.Specification;

public class OriginSpec {

	public static <T extends HasOrigin> Specification<T> isOrigin(String origin) {
		return (root, query, cb) ->
			cb.equal(
				root.get("origin"),
				origin);
	}

	public static <T extends HasOrigin> Specification<T> isAnyOrigin(List<String> origins) {
		if (origins == null || origins.isEmpty()) return null;
		if (origins.size() == 1) return isOrigin(origins.get(0));
		return (root, query, cb) ->
			root.get("origin")
				.in(origins);
	}
}
