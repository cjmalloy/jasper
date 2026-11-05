package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

import static jasper.repository.IndexRepository.HOT_LENGTHS;

@Repository
@Profile("sqlite")
@Transactional
public class IndexRepositoryImplSqlite implements IndexRepository {
	private static final Logger logger = LoggerFactory.getLogger(IndexRepositoryImplSqlite.class);

	@PersistenceContext
	private EntityManager em;

	@Override
	public void dropTags() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_tags_index").executeUpdate();
	}

	@Override
	public void buildTags() {
		// SQLite does not support GIN indexes — no-op
	}

	@Override
	public void dropExpandedTags() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_expanded_tags_index").executeUpdate();
	}

	@Override
	public void buildExpandedTags() {
		// SQLite does not support GIN indexes — no-op
	}

	@Override
	public void dropSources() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_sources_index").executeUpdate();
	}

	@Override
	public void buildSources() {
		// SQLite does not support GIN indexes — no-op
	}

	@Override
	public void dropAlts() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_alternate_urls_index").executeUpdate();
	}

	@Override
	public void buildAlts() {
		// SQLite does not support GIN indexes — no-op
	}

	@Override
	public void dropResponses() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_responses_index").executeUpdate();
	}

	@Override
	public void buildResponses() {
		// SQLite does not support GIN indexes — no-op
	}

	@Override
	public void dropInternalResponses() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_internal_responses_index").executeUpdate();
	}

	@Override
	public void buildInternalResponses() {
		// SQLite does not support GIN indexes — no-op
	}

	@Override
	public void dropFulltext() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_fulltext_index").executeUpdate();
	}

	@Override
	public void buildFulltext() {
		em.createNativeQuery("INSERT INTO ref_fts(ref_fts) VALUES('rebuild')").executeUpdate();
		em.createNativeQuery("UPDATE ref SET textsearch_en = CAST(rowid AS TEXT) WHERE textsearch_en IS NULL OR textsearch_en = ''").executeUpdate();
	}

	@Override
	public void dropPublished() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_published_index").executeUpdate();
	}

	@Override
	public void buildPublished() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_published_index ON ref (published)").executeUpdate();
	}

	@Override
	public void dropModified() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_modified_index").executeUpdate();
	}

	@Override
	public void buildModified() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_modified_index ON ref (modified)").executeUpdate();
	}

	@Override
	public void dropCreated() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_created_index").executeUpdate();
	}

	@Override
	public void buildCreated() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_created_index ON ref (created)").executeUpdate();
	}

	@Override
	public void dropMetadataModified() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_metadata_modified_index").executeUpdate();
	}

	@Override
	public void buildMetadataModified() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_metadata_modified_index ON ref (COALESCE(" + field("modified") + ", ''))").executeUpdate();
	}

	@Override
	public void dropUrlPrefix() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_url_prefix_index").executeUpdate();
	}

	@Override
	public void buildUrlPrefix() {
		// SQLite LIKE is case-insensitive, so a binary index on url cannot serve it — no-op
	}

	@Override
	public void dropNewResponse() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_new_response_index").executeUpdate();
	}

	@Override
	public void buildNewResponse() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_new_response_index ON ref (COALESCE(" + field("newResponse") + ", ''))").executeUpdate();
	}

	@Override
	public void dropNewReaction() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_new_reaction_index").executeUpdate();
	}

	@Override
	public void buildNewReaction() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_new_reaction_index ON ref (COALESCE(" + field("newReaction") + ", ''))").executeUpdate();
	}

	@Override
	public void dropIgnored() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_ignored_index").executeUpdate();
	}

	@Override
	public void buildIgnored() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_ignored_index ON ref (origin, modified) WHERE " + field("ignored") + " = 'true'").executeUpdate();
	}

	@Override
	public void dropCascade() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_cascade_index").executeUpdate();
	}

	@Override
	public void buildCascade() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_cascade_index ON ref (modified) WHERE " + field("cascade") + " = 'true'").executeUpdate();
	}

	@Override
	public void dropRegen() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_regen_index").executeUpdate();
	}

	@Override
	public void buildRegen() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_regen_index ON ref (modified) WHERE metadata IS NULL OR " + field("modified") + " IS NULL OR " + field("regen") + " = 'true'").executeUpdate();
	}

	/**
	 * Same SQL as jsonb_object_field_text(metadata, key) in SQLiteDialect, so partial indexes match the queries.
	 */
	private static String field(String key) {
		var path = "'$.\"' || REPLACE('" + key + "', '\"', '\"\"') || '\"'";
		return "(CASE WHEN json_type(metadata, " + path + ") IN ('true', 'false') THEN json_type(metadata, " + path + ") ELSE CAST(json_extract(metadata, " + path + ") AS TEXT) END)";
	}

	@Override
	public void updateHotLengths(List<String> hotLengths) {
		var wanted = new HashMap<String, String>();
		if (hotLengths != null) {
			for (var hotLength : hotLengths) {
				var key = IndexRepository.hotLength(hotLength);
				if (key == null) {
					logger.warn("Invalid hot length index: {}", hotLength);
					continue;
				}
				wanted.put(HOT_LENGTHS.get(key), hotLengthExpression(key));
			}
		}
		@SuppressWarnings("unchecked")
		List<String> existing = em.createNativeQuery("""
				SELECT name FROM sqlite_master
				WHERE type = 'index' AND tbl_name = 'ref' AND name LIKE 'ref\\_hotl\\_%' ESCAPE '\\'""")
			.getResultList();
		for (var name : existing) {
			if (wanted.containsKey(name)) continue;
			logger.info("Dropping hot length index {}", name);
			em.createNativeQuery("DROP INDEX IF EXISTS " + name).executeUpdate();
		}
		for (var e : wanted.entrySet()) {
			if (existing.contains(e.getKey())) continue;
			logger.info("Building hot length index {} on {}", e.getKey(), e.getValue());
			em.createNativeQuery("CREATE INDEX IF NOT EXISTS " + e.getKey() + " ON ref (" + e.getValue() + ")").executeUpdate();
		}
	}

	/**
	 * The same SQL that {@link jasper.repository.spec.RefSpec#sort} renders for a length sort with
	 * {@link jasper.config.SQLiteDialect}, so SQLite can match the index to the sort.
	 * The key must be validated with {@link IndexRepository#hotLength}, so it is safe to inline.
	 */
	static String hotLengthExpression(String key) {
		if ("origin".equals(key)) return "CASE WHEN origin = '' OR origin = '@' THEN 0 ELSE (LENGTH(origin) - LENGTH(REPLACE(origin, '.', '')) + 1) END";
		if (key.startsWith("metadata->")) {
			var value = "((metadata) -> ('$.\"' || REPLACE('" + key.substring("metadata->".length()) + "', '\"', '\"\"') || '\"'))";
			return "COALESCE((CASE WHEN json_valid(" + value + ") THEN (CASE WHEN json_type(" + value + ") = 'array' THEN json_array_length(" + value + ") END) END), 0)";
		}
		var column = "alternateUrls".equals(key) ? "alternate_urls" : key;
		return "COALESCE(json_array_length(" + column + "), 0)";
	}

	@Override
	public void updateHotTags(List<String> hotTags) {
		// SQLite cannot index the tag value subqueries — no-op
	}

	@Override
	public void updateHotPlugins(List<String> hotPlugins) {
		// Hot plugin indexes are only built on PostgreSQL — no-op
	}

	@Override
	public void updateHotResponses(List<String> hotResponses) {
		// Hot response indexes are only built on PostgreSQL — no-op
	}

	@Override
	public void updateHotRemoteResponses(List<String> hotRemoteResponses) {
		// Hot remote response indexes are only built on PostgreSQL — no-op
	}
}
