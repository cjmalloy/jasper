package jasper.repository;

import jasper.domain.Ref;
import jasper.domain.RefId;
import jasper.domain.proj.RefView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RefRepository extends JpaRepository<Ref, RefId>, RefMixin<Ref>, StreamMixin<RefView>, ModifiedCursor {

	@Query(value = """
		SELECT max(r.modified)
		FROM Ref r
		WHERE r.origin = :origin""")
	Instant getCursor(String origin);

	List<Ref> findAllByUrlAndPublishedGreaterThanEqual(String url, Instant date);

	@Query(nativeQuery = true, value = """
		SELECT url FROM ref
		WHERE ref.published < :date
			AND jsonb_exists(ref.sources, :url)""")
	List<String> findAllResponsesPublishedBefore(String url, Instant date);

	@Query(nativeQuery = true, value = """
		SELECT url FROM ref
		WHERE ref.origin = :origin
			AND jsonb_exists(ref.sources, :url)""")
	List<String> findAllResponsesByOrigin(String url, String origin);

	@Query(nativeQuery = true, value = """
		SELECT url FROM ref
		WHERE ref.origin = :origin
			AND jsonb_exists(ref.sources, :url)
			AND jsonb_exists(ref.tags, :tag)""")
	List<String> findAllResponsesByOriginWithTag(String url, String origin, String tag);

	@Query(nativeQuery = true, value = """
		SELECT url FROM ref
		WHERE ref.origin = :origin
			AND jsonb_exists(ref.sources, :url)
			AND jsonb_exists(ref.tags, :tag) = false""")
	List<String> findAllResponsesByOriginWithoutTag(String url, String origin, String tag);

	@Query(nativeQuery = true, value = """
		SELECT count(*) > 0 FROM ref
		WHERE ref.origin = :origin
			AND jsonb_exists(ref.alternate_urls, :url)""")
	boolean existsByAlternateUrlAndOrigin(String url, String origin);
}
