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
	void dropIgnored();
	void buildIgnored();
	void dropCascade();
	void buildCascade();
	void dropRegen();
	void buildRegen();
}
