package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!sqlite")
@Transactional
public class BackfillRepositoryImplPostgres implements BackfillRepository {

	@PersistenceContext
	private EntityManager em;

	/**
	 * Responses are filtered by their obsolete flag. Responses with missing
	 * metadata are assumed to be obsolete, except user URLs which are never obsolete.
	 * Only the user URLs of the given origin are rebuilt, user URLs from other origins are kept.
	 */
	@Override
	public int backfillMetadata(String origin, int batchSize) {
		String sql = """
			WITH rows as (
				SELECT url, origin from ref
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
					WHERE (lpre.sources @> jsonb_build_array(r.url)) AND lpre.url != r.url AND lpre.origin = r.origin AND (lpre.url ~ '^tag:/[_+]?user([/?]|$)' OR (lpre.metadata IS NOT NULL AND COALESCE(lpre.metadata->>'obsolete', 'false') IN ('false', '0'))) AND t.tag ~ '^[_+]?plugin(/|$)'
					GROUP BY t.tag
				) lp), CAST('{}' AS jsonb)),
				'remotePlugins', COALESCE((SELECT jsonb_object_agg(rp.tag, rp.cnt) FROM (
					SELECT t.tag, COUNT(DISTINCT (pre.url, pre.origin)) AS cnt FROM ref pre
						CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(pre.metadata->'expandedTags', pre.tags)) AS t(tag)
					WHERE (pre.sources @> jsonb_build_array(r.url)) AND pre.url != r.url AND (:origin = '' OR pre.origin = :origin OR pre.origin LIKE concat(:origin, '.%')) AND (pre.url ~ '^tag:/[_+]?user([/?]|$)' OR (pre.metadata IS NOT NULL AND COALESCE(pre.metadata->>'obsolete', 'false') IN ('false', '0'))) AND t.tag ~ '^[_+]?plugin(/|$)'
					GROUP BY t.tag
				) rp), CAST('{}' AS jsonb)),
				'userUrls', COALESCE((SELECT jsonb_object_agg(uu.tag, uu.urls) FROM (
					SELECT u.tag, jsonb_agg(DISTINCT u.url) AS urls FROM (
						SELECT t.tag, CASE
							WHEN ure.origin = '' THEN pu.url
							WHEN strpos(pu.url, '?') = 0 THEN concat(pu.url, ure.origin)
							ELSE overlay(pu.url PLACING concat(ure.origin, '?') FROM strpos(pu.url, '?') FOR 1)
						END AS url FROM ref ure
							CROSS JOIN LATERAL (SELECT regexp_replace(ure.url, '^tag:/[_+](user([/?]|$))', 'tag:/\\1') AS url) AS pu
							CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(ure.metadata->'expandedTags', ure.tags)) AS t(tag)
						WHERE (ure.sources @> jsonb_build_array(r.url)) AND ure.url != r.url AND ure.origin = :origin AND (ure.url ~ '^tag:/[_+]?user([/?]|$)' OR (ure.metadata IS NOT NULL AND COALESCE(ure.metadata->>'obsolete', 'false') IN ('false', '0'))) AND t.tag ~ '^[_+]?plugin/user(/|$)'
						UNION ALL
						SELECT kt.tag, ku.url FROM jsonb_each(CASE WHEN jsonb_typeof(r.metadata->'userUrls') = 'object' THEN r.metadata->'userUrls' END) AS kt(tag, urls)
							CROSS JOIN LATERAL jsonb_array_elements_text(CASE WHEN jsonb_typeof(kt.urls) = 'array' THEN kt.urls ELSE CAST('[]' AS jsonb) END) AS ku(url)
						WHERE ku.url IS NOT NULL AND COALESCE(substring(split_part(ku.url, '?', 1) FROM '@.*$'), '') != :origin
					) u
					GROUP BY u.tag
				) uu), CAST('{}' AS jsonb)),
				'obsolete', EXISTS (SELECT 1 from ref n WHERE n.url = r.url AND n.modified > r.modified AND (:origin = '' OR n.origin = :origin OR n.origin LIKE concat(:origin, '.%'))),
				'cascade', CASE WHEN jsonb_array_length(COALESCE(r.sources, '[]')) > 0 THEN true END
			))
			WHERE EXISTS (SELECT * from rows WHERE r.url = rows.url AND r.origin = rows.origin)
			""";
		em.flush();
		int updated = em.createNativeQuery(sql)
			.setParameter("origin", origin)
			.setParameter("batchSize", batchSize)
			.executeUpdate();
		em.flush();
		em.clear();
		return updated;
	}
}
