package jasper.repository.spec;

import jasper.domain.proj.HasOrigin;
import org.springframework.data.jpa.domain.Specification;

public class OriginSpec {

	public static <T extends HasOrigin> Specification<T> isOrigin(String origin) {
		return (root, query, cb) ->
			cb.equal(
				root.get("origin"),
				origin);
	}
}
