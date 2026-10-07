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
	 * metadata are assumed to be obsolete. Plugin counts only include responses
	 * in the same origin as the Ref, remote plugin counts include all origins.
	 * User URLs are never obsolete in plugin counts, since the same user tag in
	 * different origins is a different user.
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
				'responses', (SELECT jsonb_agg(re.url) FROM ref re WHERE (re.sources @> jsonb_build_array(r.url)) AND (:origin = '' OR re.origin = :origin OR re.origin LIKE concat(:origin, '.%')) AND re.metadata IS NOT NULL AND COALESCE(re.metadata->>'obsolete', 'false') IN ('false', '0') AND jsonb_exists(COALESCE(re.metadata->'expandedTags', re.tags), 'internal') = false),
				'internalResponses', (SELECT jsonb_agg(ire.url) FROM ref ire WHERE (ire.sources @> jsonb_build_array(r.url)) AND (:origin = '' OR ire.origin = :origin OR ire.origin LIKE concat(:origin, '.%')) AND ire.metadata IS NOT NULL AND COALESCE(ire.metadata->>'obsolete', 'false') IN ('false', '0') AND jsonb_exists(COALESCE(ire.metadata->'expandedTags', ire.tags), 'internal') = true),
				'plugins', COALESCE(jsonb_strip_nulls((SELECT jsonb_object_agg(
					p.tag,
					(SELECT NULLIF(COUNT(DISTINCT lpre.url), 0) FROM ref lpre WHERE (lpre.sources @> jsonb_build_array(r.url)) AND lpre.url != r.url AND lpre.origin = r.origin AND lpre.metadata IS NOT NULL AND (lpre.url LIKE 'tag:/%' OR COALESCE(lpre.metadata->>'obsolete', 'false') IN ('false', '0')) AND jsonb_exists(COALESCE(lpre.metadata->'expandedTags', lpre.tags), p.tag) = true)
				) FROM plugin p WHERE p.origin = :origin)), CAST('{}' AS jsonb)),
				'remotePlugins', COALESCE(jsonb_strip_nulls((SELECT jsonb_object_agg(
					p.tag,
					(SELECT NULLIF(COUNT(*), 0) FROM ref pre WHERE (pre.sources @> jsonb_build_array(r.url)) AND pre.url != r.url AND (:origin = '' OR pre.origin = :origin OR pre.origin LIKE concat(:origin, '.%')) AND pre.metadata IS NOT NULL AND (pre.url LIKE 'tag:/%' OR COALESCE(pre.metadata->>'obsolete', 'false') IN ('false', '0')) AND jsonb_exists(COALESCE(pre.metadata->'expandedTags', pre.tags), p.tag) = true)
				) FROM plugin p WHERE p.origin = :origin)), CAST('{}' AS jsonb)),
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
