package jasper.repository;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.domain.RefId;
import jasper.domain.proj.RefUrl;
import jasper.domain.proj.RefView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefRepository extends JpaRepository<Ref, RefId>, JpaSpecificationExecutor<Ref>, StreamMixin<RefView>, ModifiedCursor, OriginMixin {

	Optional<Ref> findOneByUrlAndOrigin(String url, String origin);
	void deleteByUrlAndOrigin(String url, String origin);
	boolean existsByUrlAndOrigin(String url, String origin);

	@Modifying
	@Query("""
		UPDATE Ref SET
			title = :title,
			comment = :comment,
			tags = :tags,
			sources = :sources,
			alternateUrls = :alternateUrls,
			plugins = :plugins,
			metadata = :metadata,
			published = :published,
			modified = :modified
		WHERE
			url = :url AND
			origin = :origin AND
			modified = :cursor""")
	int optimisticUpdate(
		Instant cursor,
		String url,
		String origin,
		String title,
		String comment,
		ArrayNode tags,
		ArrayNode sources,
		ArrayNode alternateUrls,
		ObjectNode plugins,
		Metadata metadata,
		Instant published,
		Instant modified);

	@Query("""
		SELECT max(r.modified)
		FROM Ref r
		WHERE r.origin = :origin""")
	Instant getCursor(String origin);

	@Query("""
		FROM Ref ref
		WHERE ref.url = :url
			AND ref.published >= :published
		    AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE concat(:origin, '.%'))""")
	List<Ref> findAllPublishedByUrlAndPublishedGreaterThanEqual(String url, String origin, Instant published);

	@Query(nativeQuery = true, value = """
		SELECT *, '' as scheme, false as obsolete, 0 AS tagCount, 0 AS commentCount, 0 AS responseCount, 0 AS sourceCount, 0 AS voteCount, 0 AS voteScore, 0 AS voteScoreDecay, '' as metadataModified
		FROM ref
		WHERE ref.published <= :published
			AND jsonb_exists(ref.sources, :url)
			AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE concat(:origin, '.%'))""")
	List<Ref> findAllResponsesPublishedBeforeThanEqual(String url, String origin, Instant published);

	@Query(nativeQuery = true, value = """
		SELECT url FROM ref
		WHERE jsonb_exists(ref.sources, :url)
			AND jsonb_exists(ref.tags, :tag)
		    AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE concat(:origin, '.%'))""")
	List<String> findAllResponsesWithTag(String url, String origin, String tag);

	@Query(nativeQuery = true, value = """
		SELECT url FROM ref
		WHERE jsonb_exists(ref.sources, :url)
			AND jsonb_exists(ref.tags, :tag) = false
		    AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE concat(:origin, '.%'))""")
	List<String> findAllResponsesWithoutTag(String url, String origin, String tag);

	@Query("""
		SELECT COUNT(ref) > 0 FROM Ref ref
		WHERE ref.url = :url
			AND ref.modified > :modified
		    AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE concat(:origin, '.%'))""")
	boolean existsByUrlAndModifiedGreaterThan(String url, String origin, Instant modified);

	// TODO: Sync cache
	@Modifying
	@Transactional
	@Query(nativeQuery = true, value = """
		UPDATE ref ref
		SET metadata = COALESCE(jsonb_set(metadata, '{obsolete}', CAST('true' as jsonb), true), CAST('{"obsolete":true}' as jsonb))
		WHERE ref.url = :url
			AND ref.modified < :olderThan
			AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE concat(:origin, '.%'))""")
	int setObsolete(String url, String origin, Instant olderThan);

	@Query(nativeQuery = true, value = """
		SELECT *, '' as scheme, false as obsolete, 0 AS tagCount, 0 AS commentCount, 0 AS responseCount, 0 AS sourceCount, 0 AS voteCount, 0 AS voteScore, 0 AS voteScoreDecay,
		COALESCE(metadata ->> 'modified', to_char(modified, 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"')) as metadataModified
		FROM ref
		WHERE ref.origin = :origin
			AND jsonb_exists(ref.tags, '+plugin/feed')
			AND (NOT jsonb_exists(ref.plugins->'+plugin/feed', 'lastScrape')
				OR cast(ref.plugins->'+plugin/feed'->>'lastScrape' AS timestamp) + cast(ref.plugins->'+plugin/feed'->>'scrapeInterval' AS interval) < CURRENT_TIMESTAMP AT TIME ZONE 'ZULU')
		ORDER BY cast(ref.plugins->'+plugin/feed'->>'lastScrape' AS timestamp) ASC
		LIMIT 1""")
	Optional<Ref> oldestNeedsScrapeByOrigin(String origin);

	@Query(nativeQuery = true, value = """
		SELECT *, '' as scheme, false as obsolete,  0 AS tagCount, 0 AS commentCount, 0 AS responseCount, 0 AS sourceCount, 0 AS voteCount, 0 AS voteScore, 0 AS voteScoreDecay,
		COALESCE(metadata ->> 'modified', to_char(modified, 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"')) as metadataModified
		FROM ref
		WHERE ref.origin = :origin
			AND jsonb_exists(ref.tags, '+plugin/origin/pull')
			AND (NOT jsonb_exists(ref.plugins->'+plugin/origin/pull', 'lastPull')
				OR cast(ref.plugins->'+plugin/origin/pull'->>'lastPull' AS timestamp) + cast(ref.plugins->'+plugin/origin/pull'->>'pullInterval' AS interval) < CURRENT_TIMESTAMP AT TIME ZONE 'ZULU')
		ORDER BY cast(ref.plugins->'+plugin/origin/pull'->>'lastPull' AS timestamp) ASC
		LIMIT 1""")
	Optional<Ref> oldestNeedsPullByOrigin(String origin);

	@Query(nativeQuery = true, value = """
		SELECT *, '' as scheme, false as obsolete,  0 AS tagCount, 0 AS commentCount, 0 AS responseCount, 0 AS sourceCount, 0 AS voteCount, 0 AS voteScore, 0 AS voteScoreDecay,
		COALESCE(metadata ->> 'modified', to_char(modified, 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"')) as metadataModified
		FROM ref
		WHERE ref.origin = :origin
			AND jsonb_exists(ref.tags, '+plugin/origin/push')
			AND (NOT jsonb_exists(ref.plugins->'+plugin/origin/push', 'lastPush')
				OR cast(ref.plugins->'+plugin/origin/push'->>'lastPush' AS timestamp) + cast(ref.plugins->'+plugin/origin/push'->>'pushInterval' AS interval) < CURRENT_TIMESTAMP AT TIME ZONE 'ZULU')
		ORDER BY cast(ref.plugins->'+plugin/origin/push'->>'lastPush' AS timestamp) ASC
		LIMIT 1""")
	Optional<Ref> oldestNeedsPushByOrigin(String origin);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query("""
		UPDATE Ref ref
		SET ref.metadata = NULL
		WHERE ref.metadata IS NOT NULL
		AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE concat(:origin, '.%'))""")
	void dropMetadata(String origin);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query(nativeQuery = true, value = """
		WITH rows as (
			SELECT url, origin from ref
			WHERE metadata IS NULL
			AND (:origin = '' OR origin = :origin OR origin LIKE concat(:origin, '.%'))
			LIMIT :batchSize
		)
		UPDATE ref r
		SET metadata = jsonb_strip_nulls(jsonb_build_object(
			'modified', to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
			'responses', (SELECT jsonb_agg(re.url) FROM ref re WHERE jsonb_exists(re.sources, r.url) AND NOT jsonb_exists(re.tags, 'internal') = false),
			'internalResponses', (SELECT jsonb_agg(ire.url) FROM Ref ire WHERE jsonb_exists(ire.sources, r.url) AND jsonb_exists(ire.tags, 'internal') = true),
			'plugins', jsonb_strip_nulls((SELECT jsonb_object_agg(
				p.tag,
				(SELECT jsonb_agg(pre.url) FROM ref pre WHERE jsonb_exists(pre.sources, r.url) AND jsonb_exists(pre.tags, p.tag) = true)
			) FROM plugin p WHERE p.generate_metadata = true AND p.origin = :origin)),
			'obsolete', (SELECT count(*) from ref n WHERE n.url = r.url AND n.modified > r.modified AND (:origin = '' OR n.origin = :origin OR n.origin LIKE concat(:origin, '.%')))
		))
		WHERE EXISTS (SELECT * from rows WHERE r.url = rows.url AND r.origin = rows.origin)""")
	int backfillMetadata(String origin, int batchSize);

	@Query(nativeQuery = true, value = """
		SELECT url, plugins->'+plugin/origin'->>'proxy' as proxy
		FROM ref
		WHERE ref.origin = :origin
			AND jsonb_exists(ref.tags, '+plugin/origin')
			AND (plugins->'+plugin/origin'->>'local' is NULL AND '' = :remote)
				OR plugins->'+plugin/origin'->>'local' = :remote
		LIMIT 1""")
	Optional<RefUrl> originUrl(String origin, String remote);

	@Query(nativeQuery = true, value = """
		SELECT COUNT(ref) > 0 FROM ref
		WHERE ref.plugins->'_plugin/cache'->>'id' = :id
			AND ref.plugins->'_plugin/cache'->>'ban' != 'true'
			AND ref.plugins->'_plugin/cache'->>'noStore' != 'true'""")
	boolean cacheExists(String id);
}
