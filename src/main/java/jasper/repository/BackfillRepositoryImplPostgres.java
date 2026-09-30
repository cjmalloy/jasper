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
	 * Responses are filtered for obsolescence directly from newer versions,
	 * since their obsolete flag may not have been backfilled yet.
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
				'responses', (SELECT jsonb_agg(re.url) FROM ref re WHERE jsonb_exists(re.sources, r.url) AND (:origin = '' OR re.origin = :origin OR re.origin LIKE concat(:origin, '.%')) AND NOT EXISTS (SELECT 1 FROM ref ren WHERE ren.url = re.url AND ren.modified > re.modified AND (:origin = '' OR ren.origin = :origin OR ren.origin LIKE concat(:origin, '.%'))) AND jsonb_exists(COALESCE(re.metadata->'expandedTags', re.tags), 'internal') = false),
				'internalResponses', (SELECT jsonb_agg(ire.url) FROM ref ire WHERE jsonb_exists(ire.sources, r.url) AND (:origin = '' OR ire.origin = :origin OR ire.origin LIKE concat(:origin, '.%')) AND NOT EXISTS (SELECT 1 FROM ref iren WHERE iren.url = ire.url AND iren.modified > ire.modified AND (:origin = '' OR iren.origin = :origin OR iren.origin LIKE concat(:origin, '.%'))) AND jsonb_exists(COALESCE(ire.metadata->'expandedTags', ire.tags), 'internal') = true),
				'plugins', jsonb_strip_nulls((SELECT jsonb_object_agg(
					p.tag,
					(SELECT jsonb_agg(pre.url) FROM ref pre WHERE jsonb_exists(pre.sources, r.url) AND (:origin = '' OR pre.origin = :origin OR pre.origin LIKE concat(:origin, '.%')) AND NOT EXISTS (SELECT 1 FROM ref pren WHERE pren.url = pre.url AND pren.modified > pre.modified AND (:origin = '' OR pren.origin = :origin OR pren.origin LIKE concat(:origin, '.%'))) AND jsonb_exists(COALESCE(pre.metadata->'expandedTags', pre.tags), p.tag) = true)
				) FROM plugin p WHERE p.origin = :origin)),
				'obsolete', EXISTS (SELECT 1 from ref n WHERE n.url = r.url AND n.modified > r.modified AND (:origin = '' OR n.origin = :origin OR n.origin LIKE concat(:origin, '.%'))),
				'cascade', CASE WHEN jsonb_array_length(COALESCE(r.sources, '[]')) > 0 THEN true END
			))
			WHERE EXISTS (SELECT * from rows WHERE r.url = rows.url AND r.origin = rows.origin)
			""";
		int updated = em.createNativeQuery(sql)
			.setParameter("origin", origin)
			.setParameter("batchSize", batchSize)
			.executeUpdate();
		em.flush();
		em.clear();
		return updated;
	}
}
