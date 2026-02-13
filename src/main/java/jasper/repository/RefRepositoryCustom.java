package jasper.repository;

import java.util.List;

/**
 * Custom repository fragment for Ref queries that require database-specific SQL.
 * The default implementation uses PostgreSQL syntax. When the "sqlite" profile is active,
 * SQLite-compatible queries are used instead.
 */
public interface RefRepositoryCustom {

	List<String> findAllPluginTagsInResponses(String url, String origin);

	List<String> findAllUserPluginTagsInResponses(String url, String origin);

	void dropMetadata(String origin);

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
