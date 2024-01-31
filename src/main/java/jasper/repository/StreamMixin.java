package jasper.repository;

import org.springframework.data.jpa.repository.QueryHints;

import javax.persistence.QueryHint;
import java.time.Instant;
import java.util.stream.Stream;

import static org.hibernate.jpa.QueryHints.HINT_CACHEABLE;
import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;
import static org.hibernate.jpa.QueryHints.HINT_PASS_DISTINCT_THROUGH;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

public interface StreamMixin<T> {

	@QueryHints(value = {
		@QueryHint(name = HINT_FETCH_SIZE, value = "500"),
		@QueryHint(name = HINT_CACHEABLE, value = "false"),
		@QueryHint(name = HINT_READONLY, value = "true"),
		@QueryHint(name = HINT_PASS_DISTINCT_THROUGH, value = "false")
	})
	Stream<T> streamAllByOriginAndModifiedGreaterThanEqualOrderByModifiedDesc(String origin, Instant newerThan);

	@QueryHints(value = {
		@QueryHint(name = HINT_FETCH_SIZE, value = "500"),
		@QueryHint(name = HINT_CACHEABLE, value = "false"),
		@QueryHint(name = HINT_READONLY, value = "true"),
		@QueryHint(name = HINT_PASS_DISTINCT_THROUGH, value = "false")
	})
	Stream<T> streamAllByOriginOrderByModifiedDesc(String origin);
}
