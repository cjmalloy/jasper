package jasper.repository;

import java.util.List;
import java.util.Optional;

import jasper.domain.Feed;
import jasper.domain.RefId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedRepository extends JpaRepository<Feed, RefId>, RefMixin<Feed> {
	@Query("""
		FROM Feed as f
		WHERE f.lastScrape IS NULL
			OR age(f.lastScrape) > f.scrapeInterval
		ORDER BY f.lastScrape ASC""")
	List<Feed> oldestNeedsScrape(Pageable pageable);
}
