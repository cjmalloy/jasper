package ca.hc.jasper.repository;

import java.time.Instant;
import java.util.List;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.domain.RefId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RefRepository extends JpaRepository<Ref, RefId>, RefMixin<Ref> {
	List<Ref> findAllByUrlAndPublishedAfter(String url, Instant date);
	@Query(nativeQuery = true, value = """
		SELECT * FROM ref
		WHERE ref.published < :date
			AND jsonb_exists(ref.sources, :url)""")
	List<Ref> findAllResponsesPublishedBefore(String url, Instant date);
	@Query(nativeQuery = true, value = """
		SELECT count(*) > 0 FROM ref
		WHERE jsonb_exists(ref.alternate_urls, :url)""")
	boolean existsByAlternateUrl(String url);
}
