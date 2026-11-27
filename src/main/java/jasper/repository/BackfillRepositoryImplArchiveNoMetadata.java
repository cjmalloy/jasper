package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static jasper.util.Archive.isBlank;

/**
 * Archive mode metadata backfill with the "no-metadata" profile. Archive mode is only supported on PostgreSQL.
 */
@Repository
@Profile("archive & !sqlite & no-metadata")
@Transactional
public class BackfillRepositoryImplArchiveNoMetadata implements BackfillRepository {
	/**
	 * Archive mode version of {@link BackfillRepositoryImplNoMetadata#BACKFILL}.
	 * Only the latest version in each origin is ranked, older versions are always obsolete.
	 */
	private static final String BACKFILL = """
		WITH rows as (
			SELECT url, origin, modified from ref
			WHERE (metadata IS NULL OR metadata->>'regen' = 'true')
			AND (:origin = '' OR origin = :origin OR origin LIKE concat(:origin, '.%'))
			LIMIT :batchSize
		)
		UPDATE ref r
		SET metadata = jsonb_strip_nulls(jsonb_build_object(
			'modified', COALESCE(r.metadata->>'modified', to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')),
			'expandedTags', (SELECT jsonb_agg(DISTINCT x.tag) FROM jsonb_array_elements_text(COALESCE(r.tags, '[]')) AS t(tag)
				CROSS JOIN LATERAL (SELECT t.tag UNION SELECT left(t.tag, g.n - 1) FROM generate_series(1, length(t.tag)) AS g(n) WHERE substr(t.tag, g.n, 1) = '/') AS x(tag)),
			'obsolete', EXISTS (SELECT 1 FROM ref n
				CROSS JOIN LATERAL (SELECT (CASE WHEN :pinned = true
					AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(COALESCE(n.tags, '[]')) AS t(tag) WHERE t.tag = 'plugin/pinned' OR t.tag LIKE 'plugin/pinned/%')
				THEN CASE WHEN n.origin = :origin THEN 2 ELSE 1 END ELSE 0 END) AS rank) AS nr
				CROSS JOIN LATERAL (SELECT (CASE WHEN :pinned = true
					AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(COALESCE(r.tags, '[]')) AS t(tag) WHERE t.tag = 'plugin/pinned' OR t.tag LIKE 'plugin/pinned/%')
				THEN CASE WHEN r.origin = :origin THEN 2 ELSE 1 END ELSE 0 END) AS rank) AS rr
				WHERE n.url = r.url AND (:origin = '' OR n.origin = :origin OR n.origin LIKE concat(:origin, '.%'))
					AND ((n.origin = r.origin AND n.modified > r.modified)
						OR (NOT EXISTS (SELECT 1 FROM ref nn WHERE nn.url = n.url AND nn.origin = n.origin AND nn.modified > n.modified)
							AND (nr.rank > rr.rank OR (nr.rank = rr.rank AND n.modified > r.modified))))),
			'ignored', CASE WHEN r.metadata->>'ignored' = 'true' THEN true END
		))
		WHERE EXISTS (SELECT * from rows WHERE r.url = rows.url AND r.origin = rows.origin AND r.modified = rows.modified)
		""";

	@PersistenceContext
	private EntityManager em;

	@Autowired
	PluginRepository pluginRepository;

	/**
	 * Refs are only ranked by plugin/pinned if the plugin is installed in the root origin.
	 * A blank current version is a tombstone and is treated as missing.
	 */
	private boolean pinned(String origin) {
		return pluginRepository.findByTagAndOrigin("plugin/pinned", origin)
			.filter(p -> !isBlank(p))
			.isPresent();
	}

	@Override
	public int backfillMetadata(String origin, int batchSize) {
		em.flush();
		int updated = em.createNativeQuery(BACKFILL)
			.setParameter("pinned", pinned(origin))
			.setParameter("origin", origin)
			.setParameter("batchSize", batchSize)
			.executeUpdate();
		em.flush();
		em.clear();
		return updated;
	}
}
