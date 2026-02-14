package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlite")
@Transactional
public class BackfillRepositoryImplSqlite implements BackfillRepository {

	@PersistenceContext
	private EntityManager em;

	@Override
	public int backfillMetadata(String origin, int batchSize) {
		// Single UPDATE with correlated subqueries — eliminates the N+1 query problem.
		// Uses json_patch to merge computed responses/internalResponses into the base object
		// only when they are non-empty, matching PostgreSQL's jsonb_strip_nulls behavior.
		String sql = """
			UPDATE ref SET metadata = json_patch(
				json_patch(
					json_object(
						'modified', COALESCE(json_extract(ref.metadata, '$.modified'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
						'plugins', (SELECT json_group_object(p.tag, (
							SELECT json_group_array(pre.url) FROM ref pre
							WHERE jsonb_exists(pre.sources, ref.url)
								AND jsonb_exists(COALESCE(json_extract(pre.metadata, '$.expandedTags'), pre.tags), p.tag)
						)) FROM plugin p WHERE p.origin = :origin),
						'obsolete', (SELECT count(*) FROM ref n
							WHERE n.url = ref.url AND n.modified > ref.modified
								AND (:origin = '' OR n.origin = :origin OR n.origin LIKE (:origin || '.%')))
					),
					CASE WHEN EXISTS (SELECT 1 FROM ref re
						WHERE jsonb_exists(re.sources, ref.url)
							AND NOT jsonb_exists(COALESCE(json_extract(re.metadata, '$.expandedTags'), re.tags), 'internal'))
					THEN json_object('responses', (SELECT json_group_array(re.url) FROM ref re
						WHERE jsonb_exists(re.sources, ref.url)
							AND NOT jsonb_exists(COALESCE(json_extract(re.metadata, '$.expandedTags'), re.tags), 'internal')))
					ELSE '{}' END
				),
				CASE WHEN EXISTS (SELECT 1 FROM ref ire
					WHERE jsonb_exists(ire.sources, ref.url)
						AND jsonb_exists(COALESCE(json_extract(ire.metadata, '$.expandedTags'), ire.tags), 'internal'))
				THEN json_object('internalResponses', (SELECT json_group_array(ire.url) FROM ref ire
					WHERE jsonb_exists(ire.sources, ref.url)
						AND jsonb_exists(COALESCE(json_extract(ire.metadata, '$.expandedTags'), ire.tags), 'internal')))
				ELSE '{}' END
			)
			WHERE (metadata IS NULL OR json_extract(metadata, '$.regen') = 1)
				AND (:origin = '' OR origin = :origin OR origin LIKE (:origin || '.%'))
				AND rowid IN (SELECT rowid FROM ref
					WHERE (metadata IS NULL OR json_extract(metadata, '$.regen') = 1)
						AND (:origin = '' OR origin = :origin OR origin LIKE (:origin || '.%'))
					LIMIT :batchSize)
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
