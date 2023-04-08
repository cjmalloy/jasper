package jasper.repository;

import java.time.Instant;

public interface OriginMixin {
	void deleteByOriginAndModifiedLessThanEqual(String origin, Instant olderThan);
}
