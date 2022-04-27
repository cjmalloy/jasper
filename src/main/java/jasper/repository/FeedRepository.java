package jasper.repository;

import java.time.Instant;
import java.util.Optional;

import jasper.domain.Feed;
import jasper.domain.RefId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedRepository extends JpaRepository<Feed, RefId>, RefMixin<Feed> {
	Optional<Feed> findFirstByLastScrapeBeforeOrLastScrapeIsNullOrderByLastScrapeAsc(Instant time);
}
