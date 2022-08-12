package jasper.repository;

import jasper.domain.Ref;
import jasper.domain.RefId;
import jasper.domain.proj.RefView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefRepository extends JpaRepository<Ref, RefId>, JpaSpecificationExecutor<Ref>, StreamMixin<RefView>, ModifiedCursor {

	Optional<Ref> findOneByUrlAndOrigin(String url, String origin);
	void deleteByUrlAndOrigin(String url, String origin);
	boolean existsByUrlAndOrigin(String url, String origin);
	List<Ref> findAllByUrl(String url);

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

	@Query(nativeQuery = true, value = """
		SELECT *
		FROM ref as f
		WHERE f.origin = :origin
			AND jsonb_exists(f.tags, '+plugin/feed')
			AND (NOT jsonb_exists(f.config->'+plugin/feed', 'lastScrape')
				OR f.config->'+plugin/feed'->'lastScrape'::timestamp + f.config->'+plugin/feed'->'scrapeInterval'::interval < CURRENT_TIMESTAMP AT TIME ZONE 'ZULU')
		ORDER BY f.config->'+plugin/feed'->'lastScrape'::timestamp ASC
		LIMIT 1""")
	Optional<Ref> oldestNeedsScrapeByOrigin(String origin);

	@Query(nativeQuery = true, value = """
		SELECT *
		FROM ref as f
		WHERE jsonb_exists(f.tags, '+plugin/origin')
			AND (NOT jsonb_exists(f.config->'+plugin/origin', 'lastScrape')
				OR f.config->'+plugin/origin'->'lastScrape'::timestamp + f.config->'+plugin/origin'->'scrapeInterval'::interval < CURRENT_TIMESTAMP AT TIME ZONE 'ZULU')
		ORDER BY f.config->'+plugin/origin'->'lastScrape'::timestamp ASC
		LIMIT 1""")
	Optional<Ref> oldestNeedsRepl();
}
