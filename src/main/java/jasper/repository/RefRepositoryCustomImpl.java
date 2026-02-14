package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Custom repository implementation for queries requiring database-specific native SQL.
 * Only contains methods that cannot be expressed in portable HQL (e.g., CROSS JOIN LATERAL,
 * json_each, GIN indexes, complex CTEs). All other queries use HQL @Query in RefRepository.
 */
public class RefRepositoryCustomImpl implements RefRepositoryCustom {

	@PersistenceContext
	private EntityManager em;

	@Value("${spring.jpa.database-platform:}")
	private String databasePlatform;

	private boolean isSqlite() {
		return databasePlatform.contains("SQLite");
	}

	@Override
	public List<String> findAllPluginTagsInResponses(String url, String origin) {
		String sql;
		if (isSqlite()) {
			sql = """
				SELECT DISTINCT je.value
				FROM ref r, json_each(json_extract(r.metadata, '$.expandedTags')) je
				WHERE r.url != :url
					AND jsonb_exists(r.sources, :url)
					AND (
						je.value = 'plugin'
						OR je.value LIKE 'plugin/%'
						OR je.value = '+plugin'
						OR je.value LIKE '+plugin/%'
						OR je.value = '_plugin'
						OR je.value LIKE '\\_plugin/%' ESCAPE '\\'
					)
					AND (:origin = '' OR r.origin = :origin OR r.origin LIKE (:origin || '.%'))
				""";
		} else {
			sql = """
				SELECT DISTINCT t.tag
				FROM ref r
					CROSS JOIN LATERAL jsonb_array_elements_text(r.metadata->'expandedTags') AS t(tag)
				WHERE r.url != :url
					AND jsonb_exists(r.sources, :url)
					AND t.tag ~ '^[_+]?plugin(/|$)'
					AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))
				""";
		}
		return em.createNativeQuery(sql, String.class)
			.setParameter("url", url)
			.setParameter("origin", origin)
			.getResultList();
	}

	@Override
	public List<String> findAllUserPluginTagsInResponses(String url, String origin) {
		String sql;
		if (isSqlite()) {
			sql = """
				SELECT DISTINCT je.value
				FROM ref r, json_each(json_extract(r.metadata, '$.expandedTags')) je
				WHERE r.url != :url
					AND jsonb_exists(r.sources, :url)
					AND (
						je.value = 'plugin/user'
						OR je.value = '_plugin/user'
						OR je.value = '+plugin/user'
						OR je.value LIKE 'plugin/user/%'
						OR je.value LIKE '+plugin/user/%'
						OR je.value LIKE '\\_plugin/user/%' ESCAPE '\\'
					)
					AND r.origin = :origin
				""";
		} else {
			sql = """
				SELECT DISTINCT t.tag
				FROM ref r
					CROSS JOIN LATERAL jsonb_array_elements_text(r.metadata->'expandedTags') AS t(tag)
				WHERE r.url != :url
					AND jsonb_exists(r.sources, :url)
					AND t.tag ~ '^[_+]?plugin/user(/|$)'
					AND r.origin = :origin
				""";
		}
		return em.createNativeQuery(sql, String.class)
			.setParameter("url", url)
			.setParameter("origin", origin)
			.getResultList();
	}

	@Override
	@Transactional
	public int backfillMetadata(String origin, int batchSize) {
		if (isSqlite()) {
			return backfillMetadataSqlite(origin, batchSize);
		} else {
			return backfillMetadataPostgres(origin, batchSize);
		}
	}

	private int backfillMetadataPostgres(String origin, int batchSize) {
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
				'responses', (SELECT jsonb_agg(re.url) FROM ref re WHERE jsonb_exists(re.sources, r.url) AND NOT jsonb_exists(re.metadata->'expandedTags', 'internal') = false),
				'internalResponses', (SELECT jsonb_agg(ire.url) FROM Ref ire WHERE jsonb_exists(ire.sources, r.url) AND jsonb_exists(ire.metadata->'expandedTags', 'internal') = true),
				'plugins', jsonb_strip_nulls((SELECT jsonb_object_agg(
					p.tag,
					(SELECT jsonb_agg(pre.url) FROM ref pre WHERE jsonb_exists(pre.sources, r.url) AND jsonb_exists(pre.metadata->'expandedTags', p.tag) = true)
				) FROM plugin p WHERE p.origin = :origin)),
				'obsolete', (SELECT count(*) from ref n WHERE n.url = r.url AND n.modified > r.modified AND (:origin = '' OR n.origin = :origin OR n.origin LIKE concat(:origin, '.%')))
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

	private int backfillMetadataSqlite(String origin, int batchSize) {
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

	// Index management methods - PostgreSQL uses GIN indexes, SQLite uses regular indexes or no-ops

	@Override
	@Transactional
	public void dropTags() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_tags_index").executeUpdate();
	}

	@Override
	@Transactional
	public void buildTags() {
		if (isSqlite()) {
			return;
		}
		em.createNativeQuery("CREATE INDEX ref_tags_index ON ref USING GIN(tags)").executeUpdate();
	}

	@Override
	@Transactional
	public void dropExpandedTags() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_expanded_tags_index").executeUpdate();
	}

	@Override
	@Transactional
	public void buildExpandedTags() {
		if (isSqlite()) {
			return;
		}
		em.createNativeQuery("CREATE INDEX ref_expanded_tags_index ON ref USING GIN((metadata->'expandedTags'))").executeUpdate();
	}

	@Override
	@Transactional
	public void dropSources() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_sources_index").executeUpdate();
	}

	@Override
	@Transactional
	public void buildSources() {
		if (isSqlite()) {
			return;
		}
		em.createNativeQuery("CREATE INDEX ref_sources_index ON ref USING GIN(sources)").executeUpdate();
	}

	@Override
	@Transactional
	public void dropAlts() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_alternate_urls_index").executeUpdate();
	}

	@Override
	@Transactional
	public void buildAlts() {
		if (isSqlite()) {
			return;
		}
		em.createNativeQuery("CREATE INDEX ref_alternate_urls_index ON ref USING GIN(alternate_urls)").executeUpdate();
	}

	@Override
	@Transactional
	public void dropFulltext() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_fulltext_index").executeUpdate();
	}

	@Override
	@Transactional
	public void buildFulltext() {
		if (isSqlite()) {
			em.createNativeQuery("INSERT INTO ref_fts(ref_fts) VALUES('rebuild')").executeUpdate();
			em.createNativeQuery("UPDATE ref SET textsearch_en = CAST(rowid AS TEXT) WHERE textsearch_en IS NULL OR textsearch_en = ''").executeUpdate();
			return;
		}
		em.createNativeQuery("CREATE INDEX ref_fulltext_index ON ref USING GIN(textsearch_en)").executeUpdate();
	}

	@Override
	@Transactional
	public void dropPublished() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_published_index").executeUpdate();
	}

	@Override
	@Transactional
	public void buildPublished() {
		em.createNativeQuery("CREATE INDEX ref_published_index ON ref (published)").executeUpdate();
	}

	@Override
	@Transactional
	public void dropModified() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_modified_index").executeUpdate();
	}

	@Override
	@Transactional
	public void buildModified() {
		em.createNativeQuery("CREATE INDEX ref_modified_index ON ref (modified)").executeUpdate();
	}
}
