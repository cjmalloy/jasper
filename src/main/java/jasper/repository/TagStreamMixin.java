package jasper.repository;

import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.stream.Stream;

import static org.hibernate.jpa.AvailableHints.HINT_CACHEABLE;
import static org.hibernate.jpa.AvailableHints.HINT_FETCH_SIZE;
import static org.hibernate.jpa.AvailableHints.HINT_READ_ONLY;

@Transactional(readOnly = true)
public interface TagStreamMixin<T> extends StreamMixin<T> {

	@QueryHints(value = {
		@QueryHint(name = HINT_FETCH_SIZE, value = "500"),
		@QueryHint(name = HINT_CACHEABLE, value = "false"),
		@QueryHint(name = HINT_READ_ONLY, value = "true")
	})
	Stream<T> streamAllByOriginAndModifiedGreaterThanEqualAndTagNotAndTagNotLikeOrderByModifiedDesc(String origin, Instant newerThan, String tombstone, String tombstonePattern);

	@QueryHints(value = {
		@QueryHint(name = HINT_FETCH_SIZE, value = "500"),
		@QueryHint(name = HINT_CACHEABLE, value = "false"),
		@QueryHint(name = HINT_READ_ONLY, value = "true")
	})
	Stream<T> streamAllByOriginAndTagNotAndTagNotLikeOrderByModifiedDesc(String origin, String tombstone, String tombstonePattern);

	default Stream<T> streamAllWithoutTombstonesByOriginAndModifiedGreaterThanEqualOrderByModifiedDesc(String origin, Instant newerThan) {
		return streamAllByOriginAndModifiedGreaterThanEqualAndTagNotAndTagNotLikeOrderByModifiedDesc(origin, newerThan, "deleted", "%/deleted");
	}

	default Stream<T> streamAllWithoutTombstonesByOriginOrderByModifiedDesc(String origin) {
		return streamAllByOriginAndTagNotAndTagNotLikeOrderByModifiedDesc(origin, "deleted", "%/deleted");
	}
}
