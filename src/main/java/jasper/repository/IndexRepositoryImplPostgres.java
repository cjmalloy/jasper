package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("!sqlite")
@Transactional
public class IndexRepositoryImplPostgres implements IndexRepository {

	@PersistenceContext
	private EntityManager em;

	@Override
	public void dropTags() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_tags_index").executeUpdate();
	}

	@Override
	public void buildTags() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_tags_index ON ref USING GIN(tags)").executeUpdate();
	}

	@Override
	public void dropExpandedTags() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_expanded_tags_index").executeUpdate();
	}

	@Override
	public void buildExpandedTags() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_expanded_tags_index ON ref USING GIN((metadata->'expandedTags'))").executeUpdate();
	}

	@Override
	public void dropSources() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_sources_index").executeUpdate();
	}

	@Override
	public void buildSources() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_sources_index ON ref USING GIN(sources)").executeUpdate();
	}

	@Override
	public void dropAlts() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_alternate_urls_index").executeUpdate();
	}

	@Override
	public void buildAlts() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_alternate_urls_index ON ref USING GIN(alternate_urls)").executeUpdate();
	}

	@Override
	public void dropResponses() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_responses_index").executeUpdate();
	}

	@Override
	public void buildResponses() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_responses_index ON ref USING GIN((metadata->'responses'))").executeUpdate();
	}

	@Override
	public void dropInternalResponses() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_internal_responses_index").executeUpdate();
	}

	@Override
	public void buildInternalResponses() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_internal_responses_index ON ref USING GIN((metadata->'internalResponses'))").executeUpdate();
	}

	@Override
	public void dropFulltext() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_fulltext_index").executeUpdate();
	}

	@Override
	public void buildFulltext() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_fulltext_index ON ref USING GIN(textsearch_en)").executeUpdate();
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
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_ignored_index ON ref (origin, modified) WHERE (metadata->>'ignored') = 'true'").executeUpdate();
	}

	@Override
	public void dropCascade() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_cascade_index").executeUpdate();
	}

	@Override
	public void buildCascade() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_cascade_index ON ref (modified) WHERE (metadata->>'cascade') = 'true'").executeUpdate();
	}

	@Override
	public void dropRegen() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_regen_index").executeUpdate();
	}

	@Override
	public void buildRegen() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_regen_index ON ref (modified) WHERE metadata IS NULL OR (metadata->>'modified') IS NULL OR (metadata->>'regen') = 'true'").executeUpdate();
	}
}
