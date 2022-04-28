package jasper.repository;

import java.util.Optional;

import jasper.domain.Feed;
import jasper.domain.RefId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedRepository extends JpaRepository<Feed, RefId>, RefMixin<Feed> {
	@Query("""
		FROM Feed as f
		WHERE f.lastScrape IS NULL
			OR age(f.lastScrape) > f.scrapeInterval
		ORDER BY f.lastScrape DESC""")
	Optional<Feed> oldestNeedsScrape();
}
