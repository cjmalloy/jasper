package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jasper.repository.spec.SortSpec.PluginValueFilter;
import jasper.repository.spec.SortSpec.TagValueSort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import static jasper.config.PostgreSQLDialect.SAFE_JSONB_ARRAY_LENGTH;
import static jasper.config.PostgreSQLDialect.SAFE_NUMERIC;
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

	private static final Pattern ARRAY_INDEX = Pattern.compile("^(.*)\\[(\\d+)]$");

	@Override
	public void updateHotPlugins(List<String> hotPlugins) {
		var wanted = new HashMap<String, String>();
		if (hotPlugins != null) {
			for (var hotPlugin : hotPlugins) {
				var column = hotPluginColumn(hotPlugin);
				if (column == null) {
					logger.warn("Invalid hot plugin index: {}", hotPlugin);
					continue;
				}
				wanted.put(hotPluginIndexName(column), column);
			}
		}
		@SuppressWarnings("unchecked")
		List<String> existing = em.createNativeQuery("""
				SELECT indexname FROM pg_indexes
				WHERE schemaname = current_schema() AND tablename = 'ref' AND indexname LIKE 'ref\\_hotp\\_%'""")
			.getResultList();
		for (var name : existing) {
			if (wanted.containsKey(name)) continue;
			logger.info("Dropping hot plugin index {}", name);
			em.createNativeQuery("DROP INDEX IF EXISTS " + name).executeUpdate();
		}
		for (var e : wanted.entrySet()) {
			if (existing.contains(e.getKey())) continue;
			logger.info("Building hot plugin index {} for {}", e.getKey(), e.getValue());
			em.createNativeQuery("CREATE INDEX IF NOT EXISTS " + e.getKey() + " ON ref ((" + hotPluginExpression(e.getValue()) + "))").executeUpdate();
		}
	}

	/**
	 * Parses a hot plugin sort column, e.g. "plugin/test->size:num". The "plugins->" prefix is optional.
	 *
	 * @return the sort column with the "plugins->" prefix, or null if invalid
	 */
	static String hotPluginColumn(String hotPlugin) {
		if (hotPlugin == null) return null;
		var column = hotPlugin.startsWith("plugins->") ? hotPlugin : "plugins->" + hotPlugin;
		if (!column.matches(PluginValueFilter.COLUMN_REGEX)) return null;
		return column;
	}

	/**
	 * The same SQL that {@link jasper.repository.spec.RefSpec#isPluginValueBefore} renders for a plugin filter
	 * with the PostgreSQL dialect, so the planner can match the index to plugin filters.
	 * Lengths are cast to numeric to match the comparison with the filter value.
	 * The column must be validated with {@link #hotPluginColumn}, so plugin tags and fields are safe to inline.
	 */
	static String hotPluginExpression(String column) {
		var numeric = column.endsWith(":num");
		var length = column.endsWith(":len");
		if (numeric || length) column = column.substring(0, column.length() - 4);
		var parts = column.split("->");
		var expr = "plugins";
		for (int i = 1; i < parts.length; i++) {
			var last = i == parts.length - 1;
			var m = ARRAY_INDEX.matcher(parts[i]);
			if (i > 1 && m.matches()) {
				if (!m.group(1).isEmpty()) expr = "(" + expr + ")->('" + m.group(1) + "')";
				if (!last) {
					expr = "jsonb_array_element(" + expr + ", " + m.group(2) + ")";
				} else if (length) {
					return "(" + SAFE_JSONB_ARRAY_LENGTH.replace("?1", "jsonb_array_element(" + expr + ", " + m.group(2) + ")") + ")::numeric";
				} else {
					expr = "jsonb_array_element_text(" + expr + ", " + m.group(2) + ")";
				}
			} else if (last && length) {
				return "(" + SAFE_JSONB_ARRAY_LENGTH.replace("?1", "(" + expr + ")->('" + parts[i] + "')") + ")::numeric";
			} else if (last) {
				expr = "(" + expr + ")->>('" + parts[i] + "')";
			} else {
				expr = "(" + expr + ")->('" + parts[i] + "')";
			}
		}
		if (numeric) return SAFE_NUMERIC.replace("?1", expr);
		return expr;
	}

	/**
	 * Index name for a hot plugin sort column, unique per column and within the 63 character limit.
	 */
	static String hotPluginIndexName(String column) {
		var hash = md5DigestAsHex(column.getBytes(StandardCharsets.UTF_8)).substring(0, 8);
		return "ref_hotp_" + truncate(column.substring("plugins->".length()).toLowerCase().replaceAll("[^a-z0-9]+", "_"), 40) + "_" + hash;
	}
}
