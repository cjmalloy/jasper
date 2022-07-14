package jasper.repository;

import jasper.domain.Feed;
import jasper.domain.RefId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeedRepository extends JpaRepository<Feed, RefId>, RefMixin<Feed>, StreamMixin<Feed> {
	@Query(nativeQuery = true, value = """
		SELECT *
		FROM feed as f
		WHERE f.last_scrape IS NULL
			OR f.last_scrape + f.scrape_interval < CURRENT_TIMESTAMP AT TIME ZONE 'ZULU'
		ORDER BY f.last_scrape ASC
		LIMIT 1""")
	Optional<Feed> oldestNeedsScrape();
}
