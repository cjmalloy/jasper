package jasper.repository;

import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.domain.RefId;

import java.time.Instant;
import java.util.List;

/**
 * Database-specific and archive-specific Ref queries.
 * Implementations are selected via @Profile.
 */
public interface RefRepositoryCustom {
	List<String> findAllPluginTagsInResponses(String url, String origin);
	List<Object[]> countPluginTagsInResponses(String url, String origin);
	List<Object[]> countLocalPluginTagsInResponses(String url, String origin);
	List<String> findAllUserPluginTagsInResponses(String url, String origin);
	List<RefId> findAllResponseIdsWithTag(String url, String origin, String tag);
	/**
	 * Only the highest ranked version of a Ref is not obsolete.
	 * Versions in the root origin tagged plugin/pinned rank highest, then versions in other origins
	 * tagged plugin/pinned, then everything else. The newest version wins within a rank.
	 * Refs are only ranked by plugin/pinned if the plugin is installed in the root origin.
	 */
	int updateObsolete(String url, String rootOrigin, boolean pinned);
	/**
	 * Check if a higher ranked version of a Ref exists.
	 * @see #updateObsolete(String, String, boolean)
	 */
	boolean higherRankExists(String url, String origin, String rootOrigin, boolean pinned, int rank, Instant modified);
	/**
	 * Update an existing Ref, merging the partial metadata.
	 * @return number of rows updated, 0 if the Ref should be inserted instead
	 */
	int pushAsyncMetadata(Ref ref);
	/**
	 * Write only the metadata of a Ref.
	 */
	int updateMetadata(String url, String origin, Instant modified, Metadata metadata);
	/**
	 * Queue a Ref for the cascade.
	 */
	int markCascade(String url, String origin, Instant modified);
}
