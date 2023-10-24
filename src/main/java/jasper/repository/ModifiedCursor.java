package jasper.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface ModifiedCursor<T> {
	Instant getCursor(String origin);
	Page<T> findAllByModifiedAfterOrderByModifiedAsc(Instant modified, Pageable pageable);
}
