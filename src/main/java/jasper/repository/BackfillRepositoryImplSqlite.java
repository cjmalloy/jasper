package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("sqlite")
@Transactional
public class BackfillRepositoryImplSqlite implements BackfillRepository {

	@PersistenceContext
	private EntityManager em;

	@Override
	@SuppressWarnings("unchecked")
	public int backfillMetadata(String origin, int batchSize) {
		// First, select the batch of rowids that need updating.
		// This is fast and read-only so it doesn't hold a write lock.
		String selectSql = """
			SELECT rowid FROM ref
			WHERE (metadata IS NULL OR json_extract(metadata, '$.regen') = 1)
				AND (:origin = '' OR origin = :origin OR origin LIKE (:origin || '.%'))
			LIMIT :batchSize
			""";
		List<Number> rowids = em.createNativeQuery(selectSql)
			.setParameter("origin", origin)
			.setParameter("batchSize", batchSize)
			.getResultList();
		if (rowids.isEmpty()) return 0;
		// Update each row individually so each UPDATE is small and fast,
		// minimizing the time the write lock is held on the single connection.
		String updateSql = """
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
			WHERE rowid = :rowid
			""";
		for (Number rowid : rowids) {
			em.createNativeQuery(updateSql)
				.setParameter("origin", origin)
				.setParameter("rowid", rowid.longValue())
				.executeUpdate();
		}
		em.flush();
		em.clear();
		return rowids.size();
	}
}
