package jasper.repository;

import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.List;

public interface OriginMixin {
	List<String> origins();
	@Modifying(clearAutomatically = true)
	void deleteByOriginAndModifiedLessThanEqual(String origin, Instant olderThan);
}
