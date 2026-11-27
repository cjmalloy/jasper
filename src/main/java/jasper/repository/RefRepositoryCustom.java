package jasper.repository;

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
	 * @see RefRepository#updateObsolete(String, String, boolean)
	 */
	int updateObsolete(String url, String rootOrigin, boolean pinned);
	/**
	 * @see RefRepository#higherRankExists(String, String, boolean, int, Instant)
	 */
	boolean higherRankExists(String url, String origin, String rootOrigin, boolean pinned, int rank, Instant modified);
}
