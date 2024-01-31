package jasper.repository;

import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;

public interface OriginMixin {
	@Modifying(clearAutomatically = true)
	void deleteByOriginAndModifiedLessThanEqual(String origin, Instant olderThan);
}
