package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Custom repository implementation that provides database-specific native queries.
 * Detects PostgreSQL vs SQLite based on the configured database platform and
 * dispatches to the appropriate SQL.
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
					AND (je.value LIKE 'plugin%' OR je.value LIKE '_plugin%' OR je.value LIKE '+plugin%')
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
					AND (je.value LIKE 'plugin/user%' OR je.value LIKE '_plugin/user%' OR je.value LIKE '+plugin/user%')
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
	public void dropMetadata(String origin) {
		String sql;
		if (isSqlite()) {
			sql = """
				UPDATE ref
				SET metadata = json_set(metadata, '$.regen', json('true'))
				WHERE metadata IS NOT NULL
					AND json_extract(metadata, '$.regen') != 'true'
					AND (:origin = '' OR origin = :origin OR origin LIKE (:origin || '.%'))
				""";
		} else {
			sql = """
				UPDATE ref
				SET metadata = jsonb_set(metadata, '{regen}', CAST('true' as jsonb), true)
				WHERE metadata IS NOT NULL
					AND NOT metadata->>'regen' = 'true'
					AND (:origin = '' OR origin = :origin OR origin LIKE concat(:origin, '.%'))
				""";
		}
		em.createNativeQuery(sql)
			.setParameter("origin", origin)
			.executeUpdate();
		em.flush();
		em.clear();
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
				'responses', (SELECT jsonb_agg(re.url) FROM ref re WHERE jsonb_exists(re.sources, r.url) AND NOT jsonb_exists(re.metadata->expandedTags, 'internal') = false),
				'internalResponses', (SELECT jsonb_agg(ire.url) FROM Ref ire WHERE jsonb_exists(ire.sources, r.url) AND jsonb_exists(ire.metadata->expandedTags, 'internal') = true),
				'plugins', jsonb_strip_nulls((SELECT jsonb_object_agg(
					p.tag,
					(SELECT jsonb_agg(pre.url) FROM ref pre WHERE jsonb_exists(pre.sources, r.url) AND jsonb_exists(pre.metadata->expandedTags, p.tag) = true)
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
		// SQLite implementation: process refs one at a time since SQLite doesn't support
		// the complex CTE + aggregate UPDATE pattern that PostgreSQL uses.
		String selectSql = """
			SELECT url, origin FROM ref
			WHERE (metadata IS NULL OR json_extract(metadata, '$.regen') = 'true')
				AND (:origin = '' OR origin = :origin OR origin LIKE (:origin || '.%'))
			LIMIT :batchSize
			""";
		@SuppressWarnings("unchecked")
		var rows = (List<Object[]>) em.createNativeQuery(selectSql)
			.setParameter("origin", origin)
			.setParameter("batchSize", batchSize)
			.getResultList();
		int count = 0;
		for (var row : rows) {
			String refUrl = (String) row[0];
			String refOrigin = (String) row[1];
			// Build responses list
			String responsesSql = """
				SELECT json_group_array(re.url) FROM ref re
				WHERE jsonb_exists(re.sources, :refUrl)
					AND NOT jsonb_exists(COALESCE(json_extract(re.metadata, '$.expandedTags'), re.tags), 'internal')
				""";
			String responses = (String) em.createNativeQuery(responsesSql)
				.setParameter("refUrl", refUrl)
				.getSingleResult();
			// Build internal responses list
			String internalResponsesSql = """
				SELECT json_group_array(ire.url) FROM ref ire
				WHERE jsonb_exists(ire.sources, :refUrl)
					AND jsonb_exists(COALESCE(json_extract(ire.metadata, '$.expandedTags'), ire.tags), 'internal')
				""";
			String internalResponses = (String) em.createNativeQuery(internalResponsesSql)
				.setParameter("refUrl", refUrl)
				.getSingleResult();
			// Count obsolete
			String obsoleteSql = """
				SELECT count(*) FROM ref n
				WHERE n.url = :refUrl AND n.modified > (SELECT r2.modified FROM ref r2 WHERE r2.url = :refUrl AND r2.origin = :refOrigin)
					AND (:origin = '' OR n.origin = :origin OR n.origin LIKE (:origin || '.%'))
				""";
			var obsoleteCount = em.createNativeQuery(obsoleteSql)
				.setParameter("refUrl", refUrl)
				.setParameter("refOrigin", refOrigin)
				.setParameter("origin", origin)
				.getSingleResult();
			// Get existing modified timestamp
			String modifiedSql = """
				SELECT json_extract(metadata, '$.modified') FROM ref WHERE url = :refUrl AND origin = :refOrigin
				""";
			var existingModified = em.createNativeQuery(modifiedSql)
				.setParameter("refUrl", refUrl)
				.setParameter("refOrigin", refOrigin)
				.getSingleResult();
			String modifiedTs = existingModified != null ? existingModified.toString() :
				java.time.Instant.now().toString();
			// Preserve existing plugins and userUrls from metadata, if any
			String pluginsSql = """
				SELECT json_extract(metadata, '$.plugins') FROM ref WHERE url = :refUrl AND origin = :refOrigin
				""";
			var existingPlugins = em.createNativeQuery(pluginsSql)
				.setParameter("refUrl", refUrl)
				.setParameter("refOrigin", refOrigin)
				.getSingleResult();
			String pluginsJson = existingPlugins != null ? existingPlugins.toString() : "null";
			String userUrlsSql = """
				SELECT json_extract(metadata, '$.userUrls') FROM ref WHERE url = :refUrl AND origin = :refOrigin
				""";
			var existingUserUrls = em.createNativeQuery(userUrlsSql)
				.setParameter("refUrl", refUrl)
				.setParameter("refOrigin", refOrigin)
				.getSingleResult();
			String userUrlsJson = existingUserUrls != null ? existingUserUrls.toString() : "null";
			// Build metadata JSON
			String metadata = String.format(
				"{\"modified\":%s,\"responses\":%s,\"internalResponses\":%s,\"obsolete\":%s,\"plugins\":%s,\"userUrls\":%s}",
				escapeJson(modifiedTs),
				"[]".equals(responses) || responses == null ? "null" : responses,
				"[]".equals(internalResponses) || internalResponses == null ? "null" : internalResponses,
				obsoleteCount,
				pluginsJson,
				userUrlsJson
			);
			// Update the ref
			String updateSql = """
				UPDATE ref SET metadata = json(:metadata)
				WHERE url = :refUrl AND origin = :refOrigin
				""";
			em.createNativeQuery(updateSql)
				.setParameter("metadata", metadata)
				.setParameter("refUrl", refUrl)
				.setParameter("refOrigin", refOrigin)
				.executeUpdate();
			count++;
		}
		em.flush();
		em.clear();
		return count;
	}

	private String escapeJson(String value) {
		if (value == null) return "null";
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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
			// SQLite doesn't support GIN indexes; skip
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
			// Rebuild FTS5 index from current ref data
			em.createNativeQuery("INSERT INTO ref_fts(ref_fts) VALUES('rebuild')").executeUpdate();
			// Update textsearch_en to store rowid for FTS5 correlation
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
