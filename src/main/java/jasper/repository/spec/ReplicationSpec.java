package jasper.repository.spec;

import java.time.Instant;

import jasper.domain.proj.HasModified;
import org.springframework.data.jpa.domain.Specification;

public class ReplicationSpec {

	public static <T extends HasModified> Specification<T> isModifiedAfter(Instant i) {
		if (i == null) return null;
		return (root, query, cb) ->
			cb.greaterThan(
				root.get("modified"),
				i);
	}
}
