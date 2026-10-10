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
	/**
	 * Build indexes for the given tag value sorts and drop any other hot tag indexes.
	 *
	 * @param hotTags tag value sorts, e.g. "plugin/progress:num"
	 */
	void updateHotTags(List<String> hotTags);
}
