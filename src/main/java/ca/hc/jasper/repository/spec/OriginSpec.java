package ca.hc.jasper.repository.spec;

import java.util.List;

import ca.hc.jasper.domain.proj.HasOrigin;
import ca.hc.jasper.repository.filter.QualifiedTag;
import org.springframework.data.jpa.domain.Specification;

public class OriginSpec {

	public static <T extends HasOrigin> Specification<T> isOrigin(String origin) {
		return (root, query, cb) ->
			cb.equal(
				root.get("origin"),
				origin);
	}

	public static <T extends HasOrigin> Specification<T> hasAnyOrigin(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		var spec = Specification.<T>where(null);
		for (var t : tags) {
			spec.or(t.originSpec());
		}
		return spec;
	}

	public static <T extends HasOrigin> Specification<T> hasAllOrigins(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		var spec = Specification.<T>where(null);
		for (var t : tags) {
			spec.and(t.originSpec());
		}
		return spec;
	}
}
