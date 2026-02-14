package jasper.repository;

import java.util.List;

/**
 * Custom repository fragment for Ref queries that require database-specific native SQL
 * (e.g., CROSS JOIN LATERAL, GIN indexes, complex CTEs).
 * Queries that can be expressed in portable HQL with custom dialect functions
 * should use @Query in RefRepository instead.
 */
public interface RefRepositoryCustom {

	List<String> findAllPluginTagsInResponses(String url, String origin);

	List<String> findAllUserPluginTagsInResponses(String url, String origin);

	int backfillMetadata(String origin, int batchSize);

	void dropTags();
	void buildTags();
	void dropExpandedTags();
	void buildExpandedTags();
	void dropSources();
	void buildSources();
	void dropAlts();
	void buildAlts();
	void dropFulltext();
	void buildFulltext();
	void dropPublished();
	void buildPublished();
	void dropModified();
	void buildModified();
}
