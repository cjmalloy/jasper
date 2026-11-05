package jasper.repository;

import java.util.List;

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
	void dropIgnored();
	void buildIgnored();
	void dropCascade();
	void buildCascade();
	void dropRegen();
	void buildRegen();

}
