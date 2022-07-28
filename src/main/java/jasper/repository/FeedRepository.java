package jasper.repository;

import jasper.domain.Feed;
import jasper.domain.RefId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface FeedRepository extends JpaRepository<Feed, RefId>, RefMixin<Feed>, StreamMixin<Feed>, ModifiedCursor {

	@Query(value = """
		SELECT max(f.modified)
		FROM Feed f
		WHERE f.origin = :origin""")
	Instant getCursor(String origin);

	@Query(nativeQuery = true, value = """
		SELECT *
		FROM feed as f
		WHERE f.origin = :origin
			AND (f.last_scrape IS NULL
				OR f.last_scrape + f.scrape_interval < CURRENT_TIMESTAMP AT TIME ZONE 'ZULU')
		ORDER BY f.last_scrape ASC
		LIMIT 1""")
	Optional<Feed> oldestNeedsScrapeByOrigin(String origin);
}
