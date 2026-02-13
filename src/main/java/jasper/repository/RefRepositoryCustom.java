package jasper.repository;

import jasper.domain.Ref;
import jasper.domain.proj.RefUrl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Custom repository fragment for Ref queries that require database-specific SQL.
 * The default implementation uses PostgreSQL syntax. When the "sqlite" profile is active,
 * SQLite-compatible queries are used instead.
 */
public interface RefRepositoryCustom {

	List<Ref> findAllResponsesPublishedBeforeThanEqual(String url, String origin, Instant published);

	List<String> findAllResponsesWithTag(String url, String origin, String tag);

	List<String> findAllResponsesWithoutTag(String url, String origin, String tag);

	List<String> findAllPluginTagsInResponses(String url, String origin);

	List<String> findAllUserPluginTagsInResponses(String url, String origin);

	boolean newerExists(String url, String rootOrigin, Instant newerThan);

	Optional<Ref> getRefBackfill(String origin);

	Optional<RefUrl> originUrl(String origin, String remote);

	boolean cacheExists(String id);

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
