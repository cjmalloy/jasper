package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("sqlite")
public class SqliteIndexRepository implements IndexRepository {

	@PersistenceContext
	private EntityManager em;

	@Override
	@Transactional
	public void dropTags() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_tags_index").executeUpdate();
	}

	@Override
	@Transactional
	public void buildTags() {
		// SQLite does not support GIN indexes — no-op
	}

	@Override
	@Transactional
	public void dropExpandedTags() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_expanded_tags_index").executeUpdate();
	}

	@Override
	@Transactional
	public void buildExpandedTags() {
		// SQLite does not support GIN indexes — no-op
	}

	@Override
	@Transactional
	public void dropSources() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_sources_index").executeUpdate();
	}

	@Override
	@Transactional
	public void buildSources() {
		// SQLite does not support GIN indexes — no-op
	}

	@Override
	@Transactional
	public void dropAlts() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_alternate_urls_index").executeUpdate();
	}

	@Override
	@Transactional
	public void buildAlts() {
		// SQLite does not support GIN indexes — no-op
	}

	@Override
	@Transactional
	public void dropFulltext() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_fulltext_index").executeUpdate();
	}

	@Override
	@Transactional
	public void buildFulltext() {
		em.createNativeQuery("INSERT INTO ref_fts(ref_fts) VALUES('rebuild')").executeUpdate();
		em.createNativeQuery("UPDATE ref SET textsearch_en = CAST(rowid AS TEXT) WHERE textsearch_en IS NULL OR textsearch_en = ''").executeUpdate();
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
