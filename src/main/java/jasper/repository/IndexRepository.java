package jasper.repository;

import java.util.List;
import java.util.Map;

/**
 * Database-specific index management (GIN indexes on PostgreSQL, no-ops/FTS on SQLite).
 * Implementations are selected via @Profile.
 */
public interface IndexRepository {
	void dropTags();
	void buildTags();
	void dropExpandedTags();
	void buildExpandedTags();
	void dropSources();
	void buildSources();
	void dropAlts();
	void buildAlts();
	void dropResponses();
	void buildResponses();
	void dropInternalResponses();
	void buildInternalResponses();
	void dropFulltext();
	void buildFulltext();
	void dropPublished();
	void buildPublished();
	void dropModified();
	void buildModified();
	void dropCreated();
	void buildCreated();
	void dropMetadataModified();
	void buildMetadataModified();
	void dropUrlPrefix();
	void buildUrlPrefix();
	void dropNewResponse();
	void buildNewResponse();
	void dropNewReaction();
	void buildNewReaction();
	/**
	 * Build indexes for the given tag value sorts and drop any other hot tag indexes.
	 *
	 * @param hotTags tag value sorts, e.g. "plugin/progress:num"
	 */
	void updateHotTags(List<String> hotTags);
	/**
	 * Build filter and sort indexes for the given plugin sort columns and drop any other hot plugin indexes.
	 *
	 * @param hotPlugins plugin sort columns, e.g. "plugin/test->size:num"
	 */
	void updateHotPlugins(List<String> hotPlugins);
	/**
	 * Build indexes on the local response counts (metadata->plugins) of the given plugins
	 * and drop any other hot response indexes.
	 *
	 * @param hotResponses plugin tags, e.g. "plugin/comment"
	 */
	void updateHotResponses(List<String> hotResponses);
	/**
	 * Build indexes on the response counts from all origins (metadata->remotePlugins) of the given plugins
	 * and drop any other hot remote response indexes.
	 *
	 * @param hotRemoteResponses plugin tags, e.g. "plugin/comment"
	 */
	void updateHotRemoteResponses(List<String> hotRemoteResponses);
	/**
	 * Build indexes for the given length sorts and drop any other hot length indexes.
	 *
	 * @param hotLengths length sorts, e.g. "tags", "origin:len" or "metadata->responses", see {@link #HOT_LENGTHS}
	 */
	void updateHotLengths(List<String> hotLengths);
	void dropIgnored();
	void buildIgnored();
	void dropCascade();
	void buildCascade();
	void dropRegen();
	void buildRegen();

	/**
	 * Length sorts that can be indexed, by name in the hotLengths list, with their index names.
	 * Only these columns can be indexed, so they are safe to inline into DDL.
	 */
	Map<String, String> HOT_LENGTHS = Map.of(
		"tags", "ref_hotl_tags",
		"sources", "ref_hotl_sources",
		"alternateUrls", "ref_hotl_alternate_urls",
		"origin", "ref_hotl_origin",
		"metadata->responses", "ref_hotl_responses",
		"metadata->internalResponses", "ref_hotl_internal_responses",
		"metadata->expandedTags", "ref_hotl_expanded_tags"
	);

	/**
	 * Parses a hot length, e.g. "tags", "tags:len" or "metadata->responses".
	 *
	 * @return the key in {@link #HOT_LENGTHS}, or null if the length sort cannot be indexed
	 */
	static String hotLength(String hotLength) {
		if (hotLength == null) return null;
		if (hotLength.endsWith(":len")) hotLength = hotLength.substring(0, hotLength.length() - ":len".length());
		return HOT_LENGTHS.containsKey(hotLength) ? hotLength : null;
	}
}
