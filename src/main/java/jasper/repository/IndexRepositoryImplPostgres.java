package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jasper.domain.Plugin;
import jasper.repository.spec.SortSpec;
import jasper.repository.spec.SortSpec.TagValueSort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

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
		em.createNativeQuery("DROP INDEX IF EXISTS ref_tags_len_index").executeUpdate();
	}

	@Override
	public void buildTags() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_tags_index ON ref USING GIN(tags)").executeUpdate();
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_tags_len_index ON ref ((" + arrayLength("tags") + "))").executeUpdate();
	}

	@Override
	public void dropExpandedTags() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_expanded_tags_index").executeUpdate();
		em.createNativeQuery("DROP INDEX IF EXISTS ref_expanded_tags_len_index").executeUpdate();
	}

	@Override
	public void buildExpandedTags() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_expanded_tags_index ON ref USING GIN((metadata->'expandedTags'))").executeUpdate();
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_expanded_tags_len_index ON ref ((" + metadataArrayLength("expandedTags") + "))").executeUpdate();
	}

	@Override
	public void dropSources() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_sources_index").executeUpdate();
		em.createNativeQuery("DROP INDEX IF EXISTS ref_sources_len_index").executeUpdate();
	}

	@Override
	public void buildSources() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_sources_index ON ref USING GIN(sources)").executeUpdate();
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_sources_len_index ON ref ((" + arrayLength("sources") + "))").executeUpdate();
	}

	@Override
	public void dropAlts() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_alternate_urls_index").executeUpdate();
		em.createNativeQuery("DROP INDEX IF EXISTS ref_alternate_urls_len_index").executeUpdate();
	}

	@Override
	public void buildAlts() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_alternate_urls_index ON ref USING GIN(alternate_urls)").executeUpdate();
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_alternate_urls_len_index ON ref ((" + arrayLength("alternate_urls") + "))").executeUpdate();
	}

	@Override
	public void dropResponses() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_responses_index").executeUpdate();
		em.createNativeQuery("DROP INDEX IF EXISTS ref_responses_len_index").executeUpdate();
	}

	@Override
	public void buildResponses() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_responses_index ON ref USING GIN((metadata->'responses'))").executeUpdate();
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_responses_len_index ON ref ((" + metadataArrayLength("responses") + "))").executeUpdate();
	}

	@Override
	public void dropInternalResponses() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_internal_responses_index").executeUpdate();
		em.createNativeQuery("DROP INDEX IF EXISTS ref_internal_responses_len_index").executeUpdate();
	}

	@Override
	public void buildInternalResponses() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_internal_responses_index ON ref USING GIN((metadata->'internalResponses'))").executeUpdate();
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_internal_responses_len_index ON ref ((" + metadataArrayLength("internalResponses") + "))").executeUpdate();
	}

	/**
	 * Same SQL that {@link jasper.repository.spec.RefSpec#column} renders for a "tags:len", "sources:len" or
	 * "alternateUrls:len" sort or filter with the PostgreSQL dialect, so the planner can match the index to both.
	 */
	static String arrayLength(String column) {
		return "COALESCE(jsonb_array_length(" + column + "), 0)";
	}

	/**
	 * Same SQL that {@link jasper.repository.spec.RefSpec#column} renders for a "metadata->expandedTags",
	 * "metadata->responses" or "metadata->internalResponses" sort or filter with the PostgreSQL dialect,
	 * so the planner can match the index to both.
	 */
	static String metadataArrayLength(String key) {
		var value = "(metadata)->('" + key + "')";
		return "COALESCE((CASE WHEN jsonb_typeof(" + value + ") = 'array' THEN jsonb_array_length(" + value + ") END), 0)";
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
		em.createNativeQuery("DROP INDEX IF EXISTS ref_origin_prefix_index").executeUpdate();
		em.createNativeQuery("DROP INDEX IF EXISTS ref_origin_nesting_index").executeUpdate();
	}

	@Override
	public void buildOrigin() {
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_origin_index ON ref (origin)").executeUpdate();
		// LIKE '@a.%' can only use a text_pattern_ops index with non-C collations
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_origin_prefix_index ON ref (origin text_pattern_ops)").executeUpdate();
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS ref_origin_nesting_index ON ref ((" + originNesting() + "))").executeUpdate();
	}

	/**
	 * Must match origin_nesting in PostgreSQLDialect or the planner will not use the index.
	 */
	static String originNesting() {
		return "CASE WHEN origin = '' OR origin = '@' THEN 0 ELSE (LENGTH(origin) - LENGTH(REPLACE(origin, '.', '')) + 1) END";
	}

	/**
	 * Builds an expression index matching JSONB sorts, which order missing values first ascending
	 * and last descending, so the index serves both directions and filters on the same expression.
	 * Drops an index of the same name built before sorts used NULLS FIRST.
	 */
	private void buildNullsFirst(String name, String expression) {
		var stale = em.createNativeQuery("""
				SELECT indexname FROM pg_indexes
				WHERE schemaname = current_schema() AND tablename = 'ref' AND indexname = :name
					AND indexdef NOT LIKE '%NULLS FIRST%'""")
			.setParameter("name", name)
			.getResultList();
		if (!stale.isEmpty()) em.createNativeQuery("DROP INDEX IF EXISTS " + name).executeUpdate();
		em.createNativeQuery("CREATE INDEX IF NOT EXISTS " + name + " ON ref ((" + expression + ") NULLS FIRST)").executeUpdate();
	}

	@Override
	public void dropNewResponse() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_new_response_index").executeUpdate();
	}

	/**
	 * Same SQL that {@link jasper.repository.spec.RefSpec#column} renders for a
	 * metadata date sort with the PostgreSQL dialect, so the planner can match the index to the sort and the
	 * filters on it.
	 */
	@Override
	public void buildNewResponse() {
		buildNullsFirst("ref_new_response_index", "(metadata)->>('newResponse')");
	}

	@Override
	public void dropNewReaction() {
		em.createNativeQuery("DROP INDEX IF EXISTS ref_new_reaction_index").executeUpdate();
	}

	@Override
	public void buildNewReaction() {
		buildNullsFirst("ref_new_reaction_index", "(metadata)->>('newReaction')");
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
		// Indexes without NULLS FIRST are from before missing tag values were NULL
		@SuppressWarnings("unchecked")
		List<Object[]> existing = em.createNativeQuery("""
				SELECT indexname, indexdef LIKE '%NULLS FIRST%' FROM pg_indexes
				WHERE schemaname = current_schema() AND tablename = 'ref' AND indexname LIKE 'ref\\_hot\\_%'""")
			.getResultList();
		var current = new HashSet<String>();
		for (var row : existing) {
			var name = (String) row[0];
			if (wanted.containsKey(name) && Boolean.TRUE.equals(row[1])) {
				current.add(name);
				continue;
			}
			logger.info("Dropping hot tag index {}", name);
			em.createNativeQuery("DROP INDEX IF EXISTS " + name).executeUpdate();
		}
		for (var e : wanted.entrySet()) {
			if (current.contains(e.getKey())) continue;
			logger.info("Building hot tag index {} for {} {}", e.getKey(), e.getValue().function(), e.getValue().tag());
			// Tag is validated against Tag.REGEX, so it is safe to inline
			em.createNativeQuery("CREATE INDEX IF NOT EXISTS " + e.getKey() + " ON ref ((" + e.getValue().function() + "(tags, '" + e.getValue().tag() + "')) NULLS FIRST)").executeUpdate();
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
				wanted.put(hotPluginIndexName(column), hotPluginExpression(column));
			}
		}
		// Separate sort indexes (ref_hotps_) and indexes without NULLS FIRST are from before sorts used NULLS FIRST
		@SuppressWarnings("unchecked")
		List<Object[]> existing = em.createNativeQuery("""
				SELECT indexname, indexdef LIKE '%NULLS FIRST%' FROM pg_indexes
				WHERE schemaname = current_schema() AND tablename = 'ref'
					AND (indexname LIKE 'ref\\_hotp\\_%' OR indexname LIKE 'ref\\_hotps\\_%')""")
			.getResultList();
		var current = new HashSet<String>();
		for (var row : existing) {
			var name = (String) row[0];
			if (wanted.containsKey(name) && Boolean.TRUE.equals(row[1])) {
				current.add(name);
				continue;
			}
			logger.info("Dropping hot plugin index {}", name);
			em.createNativeQuery("DROP INDEX IF EXISTS " + name).executeUpdate();
		}
		for (var e : wanted.entrySet()) {
			if (current.contains(e.getKey())) continue;
			logger.info("Building hot plugin index {} on {}", e.getKey(), e.getValue());
			em.createNativeQuery("CREATE INDEX IF NOT EXISTS " + e.getKey() + " ON ref ((" + e.getValue() + ") NULLS FIRST)").executeUpdate();
		}
	}

	@Override
	public void updateHotResponses(List<String> hotResponses) {
		updateHotResponses("plugins", hotResponses);
	}

	@Override
	public void updateHotRemoteResponses(List<String> hotRemoteResponses) {
		updateHotResponses("remotePlugins", hotRemoteResponses);
	}

	private void updateHotResponses(String field, List<String> plugins) {
		var wanted = new HashMap<String, String>();
		if (plugins != null) {
			for (var plugin : plugins) {
				if (plugin == null || !plugin.matches(Plugin.REGEX)) {
					logger.warn("Invalid hot response index on {}: {}", field, plugin);
					continue;
				}
				wanted.put(hotResponseIndexName(field, plugin), hotResponseExpression(field, plugin));
			}
		}
		var prefix = hotResponseIndexPrefix(field).replace("_", "\\_");
		@SuppressWarnings("unchecked")
		List<String> existing = em.createNativeQuery("""
				SELECT indexname FROM pg_indexes
				WHERE schemaname = current_schema() AND tablename = 'ref' AND indexname LIKE '""" + prefix + "%'")
			.getResultList();
		for (var name : existing) {
			if (wanted.containsKey(name)) continue;
			logger.info("Dropping hot response index {}", name);
			em.createNativeQuery("DROP INDEX IF EXISTS " + name).executeUpdate();
		}
		for (var e : wanted.entrySet()) {
			if (existing.contains(e.getKey())) continue;
			logger.info("Building hot response index {} on {}", e.getKey(), e.getValue());
			em.createNativeQuery("CREATE INDEX IF NOT EXISTS " + e.getKey() + " ON ref ((" + e.getValue() + "))").executeUpdate();
		}
	}

	/**
	 * The same SQL that {@link jasper.repository.spec.RefSpec#column} renders for a
	 * "metadata->plugins->{plugin}" or "metadata->remotePlugins->{plugin}" sort with the PostgreSQL dialect,
	 * which is also used by the response filters, so the planner can match the index to both.
	 * The plugin must match {@link Plugin#REGEX}, so it is safe to inline.
	 */
	static String hotResponseExpression(String field, String plugin) {
		var value = "((metadata)->('" + field + "'))->('" + plugin + "')";
		return "COALESCE((CASE WHEN jsonb_typeof(" + value + ") = 'number' THEN (" + value + ")::numeric END), 0.0)";
	}

	private static String hotResponseIndexPrefix(String field) {
		return "plugins".equals(field) ? "ref_hotr_" : "ref_hotrr_";
	}

	/**
	 * Index name for a hot response count, unique per field and plugin and within the 63 character limit.
	 */
	static String hotResponseIndexName(String field, String plugin) {
		var hash = md5DigestAsHex((field + ":" + plugin).getBytes(StandardCharsets.UTF_8)).substring(0, 8);
		return hotResponseIndexPrefix(field) + truncate(plugin.replaceAll("[^a-z0-9]+", "_"), 40) + "_" + hash;
	}

	/**
	 * Parses a hot plugin sort column, e.g. "plugin/test->size:num". The "plugins->" prefix is optional.
	 *
	 * @return the sort column with the "plugins->" prefix, or null if invalid
	 */
	static String hotPluginColumn(String hotPlugin) {
		if (hotPlugin == null) return null;
		var column = hotPlugin.startsWith("plugins->") ? hotPlugin : "plugins->" + hotPlugin;
		if (!column.matches(SortSpec.PLUGIN_COLUMN_REGEX)) return null;
		return column;
	}

	/**
	 * The same SQL that {@link jasper.repository.spec.SortSpec#jsonbColumn} renders with the PostgreSQL dialect
	 * for a plugin sort or filter, so the planner can match one NULLS FIRST index to both.
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
					var array = "jsonb_array_element(" + expr + ", " + m.group(2) + ")";
					return "(CASE WHEN jsonb_typeof(" + array + ") = 'array' THEN jsonb_array_length(" + array + ") END)";
				} else {
					expr = "jsonb_array_element" + (numeric ? "" : "_text") + "(" + expr + ", " + m.group(2) + ")";
				}
			} else if (last && length) {
				var array = "(" + expr + ")->('" + parts[i] + "')";
				return "(CASE WHEN jsonb_typeof(" + array + ") = 'array' THEN jsonb_array_length(" + array + ") END)";
			} else if (last) {
				expr = "(" + expr + ")->" + (numeric ? "" : ">") + "('" + parts[i] + "')";
			} else {
				expr = "(" + expr + ")->('" + parts[i] + "')";
			}
		}
		if (numeric) return "(CASE WHEN jsonb_typeof(" + expr + ") = 'number' THEN (" + expr + ")::numeric END)";
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
