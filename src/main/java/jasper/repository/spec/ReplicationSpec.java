package jasper.repository.spec;

import jasper.domain.proj.Cursor;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public class ReplicationSpec {

	public static <T extends Cursor> Specification<T> isModifiedAfter(Instant i) {
		if (i == null) return null;
		return (root, query, cb) ->
			cb.greaterThan(
				root.get("modified"),
				i);
	}

	public static <T extends Cursor> Specification<T> isModifiedBefore(Instant i) {
		if (i == null) return null;
		return (root, query, cb) ->
			cb.lessThan(
				root.get("modified"),
				i);
	}
}
