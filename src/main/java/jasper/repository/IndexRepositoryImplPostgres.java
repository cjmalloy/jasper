package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jasper.repository.spec.SortSpec.TagValueSort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.truncate;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

@Repository
@Profile("!sqlite")
@Transactional
public class IndexRepositoryImplPostgres implements IndexRepository {
	private static final Logger logger = LoggerFactory.getLogger(IndexRepositoryImplPostgres.class);

	@PersistenceContext
	private EntityManager em;

	@Override
	public void dropTags() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_tags_index").executeUpdate();
	}

	@Override
	public void buildTags() {
		em.createNativeQuery("CREATE INDEX ref_tags_index ON ref USING GIN(tags)").executeUpdate();
	}

	@Override
	public void dropExpandedTags() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_expanded_tags_index").executeUpdate();
	}

	@Override
	public void buildExpandedTags() {
		em.createNativeQuery("CREATE INDEX ref_expanded_tags_index ON ref USING GIN((metadata->'expandedTags'))").executeUpdate();
	}

	@Override
	public void dropSources() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_sources_index").executeUpdate();
	}

	@Override
	public void buildSources() {
		em.createNativeQuery("CREATE INDEX ref_sources_index ON ref USING GIN(sources)").executeUpdate();
	}

	@Override
	public void dropAlts() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_alternate_urls_index").executeUpdate();
	}

	@Override
	public void buildAlts() {
		em.createNativeQuery("CREATE INDEX ref_alternate_urls_index ON ref USING GIN(alternate_urls)").executeUpdate();
	}

	@Override
	public void dropResponses() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_responses_index").executeUpdate();
	}

	@Override
	public void buildResponses() {
		em.createNativeQuery("CREATE INDEX ref_responses_index ON ref USING GIN((metadata->'responses'))").executeUpdate();
	}

	@Override
	public void dropInternalResponses() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_internal_responses_index").executeUpdate();
	}

	@Override
	public void buildInternalResponses() {
		em.createNativeQuery("CREATE INDEX ref_internal_responses_index ON ref USING GIN((metadata->'internalResponses'))").executeUpdate();
	}

	@Override
	public void dropFulltext() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_fulltext_index").executeUpdate();
	}

	@Override
	public void buildFulltext() {
		em.createNativeQuery("CREATE INDEX ref_fulltext_index ON ref USING GIN(textsearch_en)").executeUpdate();
	}

	@Override
	public void dropPublished() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_published_index").executeUpdate();
	}

	@Override
	public void buildPublished() {
		em.createNativeQuery("CREATE INDEX ref_published_index ON ref (published)").executeUpdate();
	}

	@Override
	public void dropModified() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_modified_index").executeUpdate();
	}

	@Override
	public void buildModified() {
		em.createNativeQuery("CREATE INDEX ref_modified_index ON ref (modified)").executeUpdate();
	}

	@Override
	public void updateHotTags(List<String> hotTags) {
		var wanted = new HashMap<String, TagValueSort>();
		if (hotTags != null) {
			for (var hotTag : hotTags) {
				var sort = TagValueSort.parse(hotTag);
				if (sort == null) {
					logger.warn("Invalid hot tag index: {}", hotTag);
					continue;
				}
				wanted.put(hotTagIndexName(sort), sort);
			}
		}
		@SuppressWarnings("unchecked")
		List<String> existing = em.createNativeQuery("""
				SELECT indexname FROM pg_indexes
				WHERE schemaname = current_schema() AND tablename = 'ref' AND indexname LIKE 'ref\\_hot\\_%'""")
			.getResultList();
		for (var name : existing) {
			if (wanted.containsKey(name)) continue;
			logger.info("Dropping hot tag index {}", name);
			em.createNativeQuery("DROP INDEX IF EXISTS " + name).executeUpdate();
		}
		for (var e : wanted.entrySet()) {
			if (existing.contains(e.getKey())) continue;
			logger.info("Building hot tag index {} for {} {}", e.getKey(), e.getValue().function(), e.getValue().tag());
			// Tag is validated against Tag.REGEX, so it is safe to inline
			em.createNativeQuery("CREATE INDEX IF NOT EXISTS " + e.getKey() + " ON ref (" + e.getValue().function() + "(tags, '" + e.getValue().tag() + "'))").executeUpdate();
		}
	}

	/**
	 * Index name for a hot tag sort, unique per function and tag and within the 63 character limit.
	 */
	static String hotTagIndexName(TagValueSort sort) {
		var hash = md5DigestAsHex((sort.function() + ":" + sort.tag()).getBytes(StandardCharsets.UTF_8)).substring(0, 8);
		return "ref_hot_" + truncate(sort.tag().replaceAll("[^a-z0-9]+", "_"), 30) + "_" + sort.function().substring("tag_".length()) + "_" + hash;
	}
}
