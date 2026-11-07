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
		em.createNativeQuery("DROP INDEX IF EXISTS ref_tags_len_index").executeUpdate();
	}

	@Override
	public void buildTags() {
		// SQLite does not support GIN indexes, only the length index
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_tags_len_index ON ref (" + arrayLength("tags") + ")").executeUpdate();
	}

	@Override
	public void dropExpandedTags() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_expanded_tags_index").executeUpdate();
		em.createNativeQuery("DROP INDEX IF EXISTS ref_expanded_tags_len_index").executeUpdate();
	}

	@Override
	public void buildExpandedTags() {
		// SQLite does not support GIN indexes, only the length index
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_expanded_tags_len_index ON ref (" + metadataArrayLength("expandedTags") + ")").executeUpdate();
	}

	@Override
	public void dropSources() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_sources_index").executeUpdate();
		em.createNativeQuery("DROP INDEX IF EXISTS ref_sources_len_index").executeUpdate();
	}

	@Override
	public void buildSources() {
		// SQLite does not support GIN indexes, only the length index
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_sources_len_index ON ref (" + arrayLength("sources") + ")").executeUpdate();
	}

	@Override
	public void dropAlts() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_alternate_urls_index").executeUpdate();
		em.createNativeQuery("DROP INDEX IF EXISTS ref_alternate_urls_len_index").executeUpdate();
	}

	@Override
	public void buildAlts() {
		// SQLite does not support GIN indexes, only the length index
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_alternate_urls_len_index ON ref (" + arrayLength("alternate_urls") + ")").executeUpdate();
	}

	@Override
	public void dropResponses() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_responses_index").executeUpdate();
		em.createNativeQuery("DROP INDEX IF EXISTS ref_responses_len_index").executeUpdate();
	}

	@Override
	public void buildResponses() {
		// SQLite does not support GIN indexes, only the length index
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_responses_len_index ON ref (" + metadataArrayLength("responses") + ")").executeUpdate();
	}

	@Override
	public void dropInternalResponses() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_internal_responses_index").executeUpdate();
		em.createNativeQuery("DROP INDEX IF EXISTS ref_internal_responses_len_index").executeUpdate();
	}

	@Override
	public void buildInternalResponses() {
		// SQLite does not support GIN indexes, only the length index
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_internal_responses_len_index ON ref (" + metadataArrayLength("internalResponses") + ")").executeUpdate();
	}

	/**
	 * Same SQL as COALESCE(jsonb_array_length(column), 0) in SQLiteDialect, so the index matches
	 * "tags:len", "sources:len" and "alternateUrls:len" sorts and filters.
	 */
	private static String arrayLength(String column) {
		return "COALESCE(json_array_length(" + column + "), 0)";
	}

	/**
	 * Same SQL as COALESCE(safe_jsonb_array_length(jsonb_object_field(metadata, key)), 0) in SQLiteDialect, so the index
	 * matches "metadata->expandedTags", "metadata->responses" and "metadata->internalResponses" sorts and filters.
	 */
	private static String metadataArrayLength(String key) {
		var value = "((metadata) -> ('$.\"' || REPLACE('" + key + "', '\"', '\"\"') || '\"'))";
		return "COALESCE((CASE WHEN json_valid(" + value + ") THEN (CASE WHEN json_type(" + value + ") = 'array' THEN json_array_length(" + value + ") END) END), 0)";
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
	public void dropTitle() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_title_index").executeUpdate();
	}

	@Override
	public void buildTitle() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_title_index ON ref (title)").executeUpdate();
	}

	@Override
	public void dropOrigin() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_origin_index").executeUpdate();
		em.createNativeQuery("DROP INDEX IF EXISTS ref_origin_nesting_index").executeUpdate();
	}

	@Override
	public void buildOrigin() {
		// No prefix index: SQLite LIKE is case-insensitive, so sub-origin filters cannot use one
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_origin_index ON ref (origin)").executeUpdate();
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_origin_nesting_index ON ref (" + originNesting() + ")").executeUpdate();
	}

	/**
	 * Must match origin_nesting in SQLiteDialect or the planner will not use the index.
	 */
	private static String originNesting() {
		return "CASE WHEN origin = '' OR origin = '@' THEN 0 ELSE (LENGTH(origin) - LENGTH(REPLACE(origin, '.', '')) + 1) END";
	}

	@Override
	public void dropNewResponse() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_new_response_index").executeUpdate();
	}

	@Override
	public void buildNewResponse() {
		buildMetadataDate("ref_new_response_index", "newResponse");
	}

	@Override
	public void dropNewReaction() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_new_reaction_index").executeUpdate();
	}

	@Override
	public void buildNewReaction() {
		buildMetadataDate("ref_new_reaction_index", "newReaction");
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
	 * Builds an index on a metadata date, matching JSONB sorts and filters. SQLite sorts nulls first ascending,
	 * so no NULLS clause is needed. Drops an index of the same name built before sorts stopped using COALESCE.
	 */
	private void buildMetadataDate(String name, String key) {
		var stale = em.createNativeQuery("SELECT name FROM sqlite_master WHERE type = 'index' AND name = :name AND sql LIKE '%COALESCE%'")
			.setParameter("name", name)
			.getResultList();
		if (!stale.isEmpty()) em.createNativeQuery("DROP INDEX IF EXISTS " + name).executeUpdate();
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS " + name + " ON ref (" + field(key) + ")").executeUpdate();
	}

	/**
	 * Same SQL as jsonb_object_field_text(metadata, key) in SQLiteDialect, so partial indexes match the queries.
	 */
	private static String field(String key) {
		var path = "'$.\"' || REPLACE('" + key + "', '\"', '\"\"') || '\"'";
		return "(CASE WHEN json_type(metadata, " + path + ") IN ('true', 'false') THEN json_type(metadata, " + path + ") ELSE CAST(json_extract(metadata, " + path + ") AS TEXT) END)";
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
