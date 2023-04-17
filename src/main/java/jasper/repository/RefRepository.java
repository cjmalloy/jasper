package jasper.repository;

import jasper.domain.Ref;
import jasper.domain.RefId;
import jasper.domain.proj.RefView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefRepository extends JpaRepository<Ref, RefId>, JpaSpecificationExecutor<Ref>, StreamMixin<RefView>, ModifiedCursor, OriginMixin {

	Optional<Ref> findOneByUrlAndOrigin(String url, String origin);
	void deleteByUrlAndOrigin(String url, String origin);
	boolean existsByUrlAndOrigin(String url, String origin);

	@Query(value = """
		SELECT max(r.modified)
		FROM Ref r
		WHERE r.origin = :origin""")
	Instant getCursor(String origin);

	List<Ref> findAllByUrlAndPublishedGreaterThanEqual(String url, Instant date);

	@Query(nativeQuery = true, value = """
		SELECT url FROM ref
		WHERE ref.published <= :date
			AND jsonb_exists(ref.sources, :url)""")
	List<String> findAllResponsesPublishedBeforeThanEqual(String url, Instant date);

	@Query(nativeQuery = true, value = """
		SELECT url FROM ref
		WHERE jsonb_exists(ref.sources, :url)
			AND jsonb_exists(ref.tags, :tag)""")
	List<String> findAllResponsesWithTag(String url, String tag);

	@Query(nativeQuery = true, value = """
		SELECT url FROM ref
		WHERE jsonb_exists(ref.sources, :url)
			AND jsonb_exists(ref.tags, :tag) = false""")
	List<String> findAllResponsesWithoutTag(String url, String tag);

	@Query(nativeQuery = true, value = """
		SELECT count(*) > 0 FROM ref
		WHERE ref.origin = :origin
			AND jsonb_exists(ref.alternate_urls, :url)""")
	boolean existsByAlternateUrlAndOrigin(String url, String origin);

	@Query(nativeQuery = true, value = """
		SELECT *, '' as scheme,  0 AS tagCount, 0 AS commentCount, 0 AS responseCount, 0 AS sourceCount, 0 AS voteCount, 0 AS voteScore, 0 AS voteScoreDecay
		FROM ref
		WHERE ref.origin = :origin
			AND jsonb_exists(ref.tags, '+plugin/feed')
			AND (NOT jsonb_exists(ref.plugins->'+plugin/feed', 'lastScrape')
				OR cast(ref.plugins->'+plugin/feed'->>'lastScrape' AS timestamp) + cast(ref.plugins->'+plugin/feed'->>'scrapeInterval' AS interval) < CURRENT_TIMESTAMP AT TIME ZONE 'ZULU')
		ORDER BY cast(ref.plugins->'+plugin/feed'->>'lastScrape' AS timestamp) ASC
		LIMIT 1""")
	Optional<Ref> oldestNeedsScrapeByOrigin(String origin);

	@Query(nativeQuery = true, value = """
		SELECT *, '' as scheme,  0 AS tagCount, 0 AS commentCount, 0 AS responseCount, 0 AS sourceCount, 0 AS voteCount, 0 AS voteScore, 0 AS voteScoreDecay
		FROM ref
		WHERE ref.origin = :origin
			AND jsonb_exists(ref.tags, '+plugin/origin/pull')
			AND (NOT jsonb_exists(ref.plugins->'+plugin/origin/pull', 'lastPull')
				OR cast(ref.plugins->'+plugin/origin/pull'->>'lastPull' AS timestamp) + cast(ref.plugins->'+plugin/origin/pull'->>'pullInterval' AS interval) < CURRENT_TIMESTAMP AT TIME ZONE 'ZULU')
		ORDER BY cast(ref.plugins->'+plugin/origin/pull'->>'lastPull' AS timestamp) ASC
		LIMIT 1""")
	Optional<Ref> oldestNeedsPullByOrigin(String origin);

	@Query(nativeQuery = true, value = """
		SELECT *, '' as scheme,  0 AS tagCount, 0 AS commentCount, 0 AS responseCount, 0 AS sourceCount, 0 AS voteCount, 0 AS voteScore, 0 AS voteScoreDecay
		FROM ref
		WHERE ref.origin = :origin
			AND jsonb_exists(ref.tags, '+plugin/origin/push')
			AND (NOT jsonb_exists(ref.plugins->'+plugin/origin/push', 'lastPush')
				OR cast(ref.plugins->'+plugin/origin/push'->>'lastPush' AS timestamp) + cast(ref.plugins->'+plugin/origin/push'->>'pushInterval' AS interval) < CURRENT_TIMESTAMP AT TIME ZONE 'ZULU')
		ORDER BY cast(ref.plugins->'+plugin/origin/push'->>'lastPush' AS timestamp) ASC
		LIMIT 1""")
	Optional<Ref> oldestNeedsPushByOrigin(String origin);

	@Modifying
	@Query(nativeQuery = true, value = """
		UPDATE ref r
		SET metadata = jsonb_strip_nulls(jsonb_build_object(
			'modified', to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
			'responses', (SELECT jsonb_agg(re.url) FROM ref re WHERE jsonb_exists(re.sources, :url) AND jsonb_exists(re.tags, 'internal') = false),
			'internalResponses', (SELECT jsonb_agg(ire.url) FROM Ref ire WHERE jsonb_exists(ire.sources, :url) AND jsonb_exists(ire.tags, 'internal') = true),
			'plugins', jsonb_strip_nulls((SELECT jsonb_object_agg(
				p.tag,
				(SELECT jsonb_agg(pre.url) FROM ref pre WHERE jsonb_exists(pre.sources, :url) AND jsonb_exists(pre.tags, p.tag) = true)
			) FROM plugin p WHERE p.generate_metadata = true AND p.origin = :validationOrigin))
		))
		WHERE r.url = :url AND r.origin = :origin""")
	int updateMetadataByUrlAndOrigin(String url, String origin, String validationOrigin);

	@Modifying
	@Query(nativeQuery = true, value = """
		UPDATE ref r
		SET metadata = jsonb_strip_nulls(jsonb_build_object(
			'modified', to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
			'responses', metadata->'responses' - :response,
			'internalResponses', metadata->'internalResponses' - :response,
			'plugins', ((SELECT jsonb_object_agg(
				p.key,
				p.value - :response
			) FROM json_each(metadata->'plugins') p))
		))
		WHERE r.url = :source""")
	int removeSourceMetadataByUrl(String source, String response);

	@Modifying
	@Query(nativeQuery = true, value = """
		UPDATE ref r
		SET metadata = jsonb_strip_nulls(jsonb_build_object(
			'modified', to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
			'responses', CASE WHEN ((select count(*) from ref re where re.url = :response and NOT jsonb_exists(re.tags, 'internal')) > 0)
				THEN metadata->'responses'
				ELSE metadata->'responses' || :response END,
			'internalResponses', CASE WHEN ((select count(*) from ref ire where ire.url = :response and jsonb_exists(ire.tags, 'internal')) > 0)
				THEN metadata->'internalResponses' || :response
				ELSE metadata->'internalResponses' END,
			'plugins', jsonb_strip_nulls((SELECT jsonb_object_agg(
				p.tag,
				CASE WHEN ((select count(*) from ref pre where pre.url = :response and jsonb_exists(pre.tags, p.tag)) > 0)
					THEN metadata->'plugins'->p.tag
					ELSE coalesce(metadata->'plugins'->p.tag, '[]'\\:\\:jsonb) || :response END,
			) FROM plugin p WHERE p.generate_metadata = true AND p.origin = :validationOrigin))
		))
		WHERE r.url = :source""")
	int addSourceMetadataByUrl(String source, String response, String validationOrigin);

	@Modifying
	@Query(nativeQuery = true, value = """
		UPDATE ref r
		SET metadata = jsonb_strip_nulls(jsonb_build_object(
			'modified', to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
			'responses', (SELECT jsonb_agg(re.url) FROM ref re WHERE jsonb_exists(re.sources, r.url) AND NOT jsonb_exists(re.tags, 'internal') = false),
			'internalResponses', (SELECT jsonb_agg(ire.url) FROM Ref ire WHERE jsonb_exists(ire.sources, r.url) AND jsonb_exists(ire.tags, 'internal') = true),
			'plugins', jsonb_strip_nulls((SELECT jsonb_object_agg(
				p.tag,
				(SELECT jsonb_agg(pre.url) FROM ref pre WHERE jsonb_exists(pre.sources, r.url) AND jsonb_exists(pre.tags, p.tag) = true)
			) FROM plugin p WHERE p.generate_metadata = true AND p.origin = :validationOrigin))
		))""")
	int backfill(String validationOrigin);

}
