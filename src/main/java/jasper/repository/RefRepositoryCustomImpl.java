package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jasper.domain.Ref;
import jasper.domain.proj.RefUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
	@SuppressWarnings("unchecked")
	public List<Ref> findAllResponsesPublishedBeforeThanEqual(String url, String origin, Instant published) {
		String sql;
		if (isSqlite()) {
			sql = """
				SELECT *, '' as scheme
				FROM ref
				WHERE ref.url != :url
					AND ref.published <= :published
					AND jsonb_exists(ref.sources, :url)
					AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE (:origin || '.%'))
				""";
		} else {
			sql = """
				SELECT *, '' as scheme
				FROM ref
				WHERE ref.url != :url
					AND ref.published <= :published
					AND jsonb_exists(ref.sources, :url)
					AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE concat(:origin, '.%'))
				""";
		}
		return em.createNativeQuery(sql, Ref.class)
			.setParameter("url", url)
			.setParameter("origin", origin)
			.setParameter("published", published)
			.getResultList();
	}

	@Override
	public List<String> findAllResponsesWithTag(String url, String origin, String tag) {
		String sql;
		if (isSqlite()) {
			sql = """
				SELECT url FROM ref
				WHERE ref.url != :url
					AND jsonb_exists(ref.sources, :url)
					AND jsonb_exists(COALESCE(json_extract(ref.metadata, '$.expandedTags'), ref.tags), :tag)
					AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE (:origin || '.%'))
				""";
		} else {
			sql = """
				SELECT url FROM ref
				WHERE ref.url != :url
					AND jsonb_exists(ref.sources, :url)
					AND jsonb_exists(COALESCE(ref.metadata->'expandedTags', ref.tags), :tag)
					AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE concat(:origin, '.%'))
				""";
		}
		return em.createNativeQuery(sql, String.class)
			.setParameter("url", url)
			.setParameter("origin", origin)
			.setParameter("tag", tag)
			.getResultList();
	}

	@Override
	public List<String> findAllResponsesWithoutTag(String url, String origin, String tag) {
		String sql;
		if (isSqlite()) {
			sql = """
				SELECT url FROM ref
				WHERE ref.url != :url
					AND jsonb_exists(ref.sources, :url)
					AND NOT jsonb_exists(COALESCE(json_extract(ref.metadata, '$.expandedTags'), ref.tags), :tag)
					AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE (:origin || '.%'))
				""";
		} else {
			sql = """
				SELECT url FROM ref
				WHERE ref.url != :url
					AND jsonb_exists(ref.sources, :url)
					AND NOT jsonb_exists(COALESCE(ref.metadata->'expandedTags', ref.tags), :tag)
					AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE concat(:origin, '.%'))
				""";
		}
		return em.createNativeQuery(sql, String.class)
			.setParameter("url", url)
			.setParameter("origin", origin)
			.setParameter("tag", tag)
			.getResultList();
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
				'responses', (SELECT jsonb_agg(re.url) FROM ref re WHERE jsonb_exists(re.sources, r.url) AND NOT jsonb_exists(COALESCE(re.metadata->'expandedTags', re.tags), 'internal')),
				'internalResponses', (SELECT jsonb_agg(ire.url) FROM ref ire WHERE jsonb_exists(ire.sources, r.url) AND jsonb_exists(COALESCE(ire.metadata->'expandedTags', ire.tags), 'internal')),
				'plugins', jsonb_strip_nulls((SELECT jsonb_object_agg(
					p.tag,
					(SELECT jsonb_agg(pre.url) FROM ref pre WHERE jsonb_exists(pre.sources, r.url) AND jsonb_exists(COALESCE(pre.metadata->'expandedTags', pre.tags), p.tag))
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
			var pluginsList = em.createNativeQuery(pluginsSql)
				.setParameter("refUrl", refUrl)
				.setParameter("refOrigin", refOrigin)
				.getResultList();
			String pluginsJson = !pluginsList.isEmpty() && pluginsList.getFirst() != null ? pluginsList.getFirst().toString() : "null";
			String userUrlsSql = """
				SELECT json_extract(metadata, '$.userUrls') FROM ref WHERE url = :refUrl AND origin = :refOrigin
				""";
			var userUrlsList = em.createNativeQuery(userUrlsSql)
				.setParameter("refUrl", refUrl)
				.setParameter("refOrigin", refOrigin)
				.getResultList();
			String userUrlsJson = !userUrlsList.isEmpty() && userUrlsList.getFirst() != null ? userUrlsList.getFirst().toString() : "null";
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

	@Override
	public boolean newerExists(String url, String rootOrigin, Instant newerThan) {
		String sql;
		if (isSqlite()) {
			sql = """
				SELECT COUNT(*) FROM ref
				WHERE url = :url
				  AND modified > :newerThan
				  AND (:rootOrigin = '' OR origin = :rootOrigin OR origin LIKE (:rootOrigin || '.%'))
				""";
		} else {
			sql = """
				SELECT COUNT(*) FROM ref
				WHERE url = :url
				  AND modified > :newerThan
				  AND (:rootOrigin = '' OR origin = :rootOrigin OR origin LIKE concat(:rootOrigin, '.%'))
				""";
		}
		var result = em.createNativeQuery(sql)
			.setParameter("url", url)
			.setParameter("rootOrigin", rootOrigin)
			.setParameter("newerThan", newerThan)
			.getSingleResult();
		return ((Number) result).longValue() > 0;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Optional<Ref> getRefBackfill(String origin) {
		String sql;
		if (isSqlite()) {
			sql = """
				SELECT *, '' as scheme
				FROM ref
				WHERE (metadata IS NULL OR json_extract(metadata, '$.modified') IS NULL OR json_extract(metadata, '$.regen') = 'true')
					AND (:origin = '' OR origin = :origin OR origin LIKE (:origin || '.%'))
				ORDER BY modified DESC
				LIMIT 1
				""";
		} else {
			sql = """
				SELECT *, '' as scheme
				FROM ref
				WHERE (metadata IS NULL OR NOT jsonb_exists(metadata, 'modified') OR metadata->>'regen' = 'true')
					AND (:origin = '' OR origin = :origin OR origin LIKE concat(:origin, '.%'))
				ORDER BY modified DESC
				LIMIT 1
				""";
		}
		List<Ref> result = em.createNativeQuery(sql, Ref.class)
			.setParameter("origin", origin)
			.getResultList();
		return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
	}

	@Override
	@SuppressWarnings("unchecked")
	public Optional<RefUrl> originUrl(String origin, String remote) {
		String sql;
		if (isSqlite()) {
			sql = """
				SELECT url, json_extract(plugins, '$."+plugin/origin".proxy') as proxy
				FROM ref
				WHERE ref.origin = :origin
					AND (CASE WHEN json_type(COALESCE(json_extract(ref.metadata, '$.expandedTags'), ref.tags)) = 'object'
						THEN ('+plugin/origin' IN (SELECT je.key FROM json_each(COALESCE(json_extract(ref.metadata, '$.expandedTags'), ref.tags)) je))
						ELSE ('+plugin/origin' IN (SELECT je.value FROM json_each(COALESCE(json_extract(ref.metadata, '$.expandedTags'), ref.tags)) je))
					END)
					AND ((json_extract(plugins, '$."+plugin/origin".local') IS NULL AND '' = :remote)
						OR json_extract(plugins, '$."+plugin/origin".local') = :remote)
				LIMIT 1
				""";
		} else {
			sql = """
				SELECT url, plugins->'+plugin/origin'->>'proxy' as proxy
				FROM ref
				WHERE ref.origin = :origin
					AND jsonb_exists(COALESCE(ref.metadata->'expandedTags', ref.tags), '+plugin/origin')
					AND ((plugins->'+plugin/origin'->>'local' is NULL AND '' = :remote)
						OR plugins->'+plugin/origin'->>'local' = :remote)
				LIMIT 1
				""";
		}
		var result = em.createNativeQuery(sql)
			.setParameter("origin", origin)
			.setParameter("remote", remote)
			.getResultList();
		if (result.isEmpty()) return Optional.empty();
		Object[] row = (Object[]) result.getFirst();
		String url = (String) row[0];
		String proxy = (String) row[1];
		return Optional.of(new RefUrl() {
			@Override public String getUrl() { return url; }
			@Override public String getProxy() { return proxy; }
		});
	}

	@Override
	public boolean cacheExists(String id) {
		String sql;
		if (isSqlite()) {
			sql = """
				SELECT COUNT(*) FROM ref
				WHERE json_extract(ref.plugins, '$."_plugin/cache".id') = :id
					AND COALESCE(json_extract(ref.plugins, '$."_plugin/cache".ban'), '') != 'true'
					AND COALESCE(json_extract(ref.plugins, '$."_plugin/cache".noStore'), '') != 'true'
				""";
		} else {
			sql = """
				SELECT COUNT(*) FROM ref
				WHERE ref.plugins->'_plugin/cache'->>'id' = :id
					AND ref.plugins->'_plugin/cache'->>'ban' != 'true'
					AND ref.plugins->'_plugin/cache'->>'noStore' != 'true'
				""";
		}
		var result = em.createNativeQuery(sql)
			.setParameter("id", id)
			.getSingleResult();
		return ((Number) result).longValue() > 0;
	}
}
