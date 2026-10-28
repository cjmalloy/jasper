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
public class IndexRepositoryImplSqlite implements IndexRepository {

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
	public void updateHotTags(List<String> hotTags) {
		// SQLite cannot index the tag value subqueries — no-op
	}
}
