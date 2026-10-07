package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!sqlite")
@Transactional
public class BackfillRepositoryImplPostgres implements BackfillRepository {

	/**
	 * Responses are filtered by their obsolete flag. Responses with missing
	 * metadata are assumed to be obsolete, except user URLs which are never obsolete.
	 * Older versions of a user URL in archive mode are skipped.
	 * Rows are matched by modified so only a single version is updated in archive mode.
	 */
	private static final String BACKFILL_METADATA = """
		WITH rows as (
			SELECT url, origin, modified from ref
			WHERE (metadata IS NULL OR metadata->>'regen' = 'true')
			AND (:origin = '' OR origin = :origin OR origin LIKE concat(:origin, '.%'))
			LIMIT :batchSize
		)
		UPDATE ref r
		SET metadata = jsonb_strip_nulls(jsonb_build_object(
			'modified', COALESCE(r.metadata->>'modified', to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')),
			'newResponse', r.metadata->>'newResponse',
			'newReaction', r.metadata->>'newReaction',
			'responses', (SELECT jsonb_agg(re.url) FROM ref re WHERE (re.sources @> jsonb_build_array(r.url)) AND (:origin = '' OR re.origin = :origin OR re.origin LIKE concat(:origin, '.%')) AND re.metadata IS NOT NULL AND COALESCE(re.metadata->>'obsolete', 'false') IN ('false', '0') AND jsonb_exists(COALESCE(re.metadata->'expandedTags', re.tags), 'internal') = false),
			'internalResponses', (SELECT jsonb_agg(ire.url) FROM ref ire WHERE (ire.sources @> jsonb_build_array(r.url)) AND (:origin = '' OR ire.origin = :origin OR ire.origin LIKE concat(:origin, '.%')) AND ire.metadata IS NOT NULL AND COALESCE(ire.metadata->>'obsolete', 'false') IN ('false', '0') AND jsonb_exists(COALESCE(ire.metadata->'expandedTags', ire.tags), 'internal') = true),
			'plugins', COALESCE((SELECT jsonb_object_agg(lp.tag, lp.cnt) FROM (
				SELECT t.tag, COUNT(DISTINCT lpre.url) AS cnt FROM ref lpre
					CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(lpre.metadata->'expandedTags', lpre.tags)) AS t(tag)
				WHERE (lpre.sources @> jsonb_build_array(r.url)) AND lpre.url != r.url AND lpre.origin = r.origin AND ((lpre.url ~ '^tag:/[_+]?user([/?]|$)' AND NOT EXISTS (SELECT 1 FROM ref lpren WHERE lpren.url = lpre.url AND lpren.origin = lpre.origin AND lpren.modified > lpre.modified)) OR (lpre.metadata IS NOT NULL AND COALESCE(lpre.metadata->>'obsolete', 'false') IN ('false', '0'))) AND t.tag ~ '^[_+]?plugin(/|$)'
				GROUP BY t.tag
			) lp), CAST('{}' AS jsonb)),
			'remotePlugins', COALESCE((SELECT jsonb_object_agg(rp.tag, rp.cnt) FROM (
				SELECT t.tag, COUNT(DISTINCT (pre.url, pre.origin)) AS cnt FROM ref pre
					CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(pre.metadata->'expandedTags', pre.tags)) AS t(tag)
				WHERE (pre.sources @> jsonb_build_array(r.url)) AND pre.url != r.url AND (:origin = '' OR pre.origin = :origin OR pre.origin LIKE concat(:origin, '.%')) AND ((pre.url ~ '^tag:/[_+]?user([/?]|$)' AND NOT EXISTS (SELECT 1 FROM ref pren WHERE pren.url = pre.url AND pren.origin = pre.origin AND pren.modified > pre.modified)) OR (pre.metadata IS NOT NULL AND COALESCE(pre.metadata->>'obsolete', 'false') IN ('false', '0'))) AND t.tag ~ '^[_+]?plugin(/|$)'
				GROUP BY t.tag
			) rp), CAST('{}' AS jsonb)),
			'userUrls', COALESCE((SELECT jsonb_object_agg(uu.tag, uu.urls) FROM (
				SELECT t.tag, jsonb_agg(CASE
					WHEN ure.origin = '' THEN ure.url
					WHEN strpos(ure.url, '?') = 0 THEN concat(ure.url, ure.origin)
					ELSE overlay(ure.url PLACING concat(ure.origin, '?') FROM strpos(ure.url, '?') FOR 1)
				END) AS urls FROM ref ure
					CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(ure.metadata->'expandedTags', ure.tags)) AS t(tag)
				WHERE (ure.sources @> jsonb_build_array(r.url)) AND ure.url != r.url AND ure.origin = :origin AND ((ure.url ~ '^tag:/[_+]?user([/?]|$)' AND NOT EXISTS (SELECT 1 FROM ref uren WHERE uren.url = ure.url AND uren.origin = ure.origin AND uren.modified > ure.modified)) OR (ure.metadata IS NOT NULL AND COALESCE(ure.metadata->>'obsolete', 'false') IN ('false', '0'))) AND t.tag ~ '^[_+]?plugin/user(/|$)'
				GROUP BY t.tag
			) uu), CAST('{}' AS jsonb)),
			'obsolete', EXISTS (SELECT 1 from ref n WHERE n.url = r.url AND n.modified > r.modified AND (:origin = '' OR n.origin = :origin OR n.origin LIKE concat(:origin, '.%'))),
			'cascade', CASE WHEN jsonb_array_length(COALESCE(r.sources, '[]')) > 0 THEN true END
		))
		WHERE EXISTS (SELECT * from rows WHERE r.url = rows.url AND r.origin = rows.origin AND r.modified = rows.modified)
		""";

	/**
	 * Responses, plugin counts and cascade are not tracked when the
	 * "no-metadata" profile is active. Expanded tags are kept.
	 */
	private static final String BACKFILL_NO_METADATA = """
		WITH rows as (
			SELECT url, origin, modified from ref
			WHERE (metadata IS NULL OR metadata->>'regen' = 'true')
			AND (:origin = '' OR origin = :origin OR origin LIKE concat(:origin, '.%'))
			LIMIT :batchSize
		)
		UPDATE ref r
		SET metadata = jsonb_strip_nulls(jsonb_build_object(
			'modified', COALESCE(r.metadata->>'modified', to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')),
			'expandedTags', r.metadata->'expandedTags',
			'obsolete', EXISTS (SELECT 1 from ref n WHERE n.url = r.url AND n.modified > r.modified AND (:origin = '' OR n.origin = :origin OR n.origin LIKE concat(:origin, '.%')))
		))
		WHERE EXISTS (SELECT * from rows WHERE r.url = rows.url AND r.origin = rows.origin AND r.modified = rows.modified)
		""";

	@PersistenceContext
	private EntityManager em;

	@Value("#{environment.matchesProfiles('no-metadata')}")
	boolean noMetadata;

	@Override
	public int backfillMetadata(String origin, int batchSize) {
		em.flush();
		int updated = em.createNativeQuery(noMetadata ? BACKFILL_NO_METADATA : BACKFILL_METADATA)
			.setParameter("origin", origin)
			.setParameter("batchSize", batchSize)
			.executeUpdate();
		em.flush();
		em.clear();
		return updated;
	}
}
