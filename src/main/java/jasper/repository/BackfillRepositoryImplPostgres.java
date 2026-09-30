package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("!sqlite")
@Transactional
public class BackfillRepositoryImplPostgres implements BackfillRepository {

	@PersistenceContext
	private EntityManager em;

	/**
	 * Responses with missing metadata, or a non-boolean obsolete flag, are
	 * assumed to be obsolete. Once they are backfilled, any source whose
	 * metadata no longer matches is flagged for regen.
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
				'responses', (SELECT jsonb_agg(re.url) FROM ref re WHERE jsonb_exists(re.sources, r.url) AND re.metadata IS NOT NULL AND COALESCE(re.metadata->>'obsolete', 'false') IN ('false', '0') AND jsonb_exists(COALESCE(re.metadata->'expandedTags', re.tags), 'internal') = false),
				'internalResponses', (SELECT jsonb_agg(ire.url) FROM ref ire WHERE jsonb_exists(ire.sources, r.url) AND ire.metadata IS NOT NULL AND COALESCE(ire.metadata->>'obsolete', 'false') IN ('false', '0') AND jsonb_exists(COALESCE(ire.metadata->'expandedTags', ire.tags), 'internal') = true),
				'plugins', jsonb_strip_nulls((SELECT jsonb_object_agg(
					p.tag,
					(SELECT jsonb_agg(pre.url) FROM ref pre WHERE jsonb_exists(pre.sources, r.url) AND pre.metadata IS NOT NULL AND COALESCE(pre.metadata->>'obsolete', 'false') IN ('false', '0') AND jsonb_exists(COALESCE(pre.metadata->'expandedTags', pre.tags), p.tag) = true)
				) FROM plugin p WHERE p.origin = :origin)),
				'obsolete', EXISTS (SELECT 1 from ref n WHERE n.url = r.url AND n.modified > r.modified AND (:origin = '' OR n.origin = :origin OR n.origin LIKE concat(:origin, '.%')))
			))
			WHERE EXISTS (SELECT * from rows WHERE r.url = rows.url AND r.origin = rows.origin)
			RETURNING r.url
			""";
		List<String> updated = em.createNativeQuery(sql, String.class)
			.setParameter("origin", origin)
			.setParameter("batchSize", batchSize)
			.getResultList();
		if (!updated.isEmpty()) {
			String regenSources = """
				UPDATE ref p
				SET metadata = jsonb_set(p.metadata, '{regen}', to_jsonb(true))
				FROM (
					SELECT DISTINCT x.url, s.source
					FROM ref x
						CROSS JOIN LATERAL jsonb_array_elements_text(x.sources) AS s(source)
					WHERE x.url IN (:urls)
				) c
				WHERE p.url = c.source
					AND p.metadata IS NOT NULL
					AND COALESCE(p.metadata->>'regen', 'false') != 'true'
					AND (:origin = '' OR p.origin = :origin OR p.origin LIKE concat(:origin, '.%'))
					AND (SELECT count(*) FROM ref a WHERE a.url = c.url AND jsonb_exists(a.sources, p.url) AND a.metadata IS NOT NULL AND COALESCE(a.metadata->>'obsolete', 'false') IN ('false', '0'))
						!= (SELECT count(*) FROM jsonb_array_elements_text(COALESCE(p.metadata->'responses', '[]') || COALESCE(p.metadata->'internalResponses', '[]')) AS e(url) WHERE e.url = c.url)
				""";
			em.createNativeQuery(regenSources)
				.setParameter("origin", origin)
				.setParameter("urls", updated)
				.executeUpdate();
		}
		em.flush();
		em.clear();
		return updated.size();
	}
}
