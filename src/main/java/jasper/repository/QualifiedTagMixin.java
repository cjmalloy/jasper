package jasper.repository;

import jasper.domain.proj.Tag;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Deleting tags has two semantics:
 * <ul>
 *     <li>Hard delete ({@link #deleteByQualifiedTag}): removes every row of the tag. In archive mode this
 *     purges all versions, and explicit local deletes also purge the deletor tag. Used for explicit local
 *     deletes and for applying delete notices on regular servers.</li>
 *     <li>Delete notices (deletor tags) in archive mode: stored as a new version and never remove rows.
 *     A tag is deleted if its latest deletor is newer than its latest version, see {@link #existsLiveByQualifiedTag}.</li>
 * </ul>
 */
@Transactional(readOnly = true)
public interface QualifiedTagMixin<T extends Tag> extends JpaSpecificationExecutor<T> {
	Optional<T> findFirstByQualifiedTagOrderByModifiedDesc(String tag);

	/**
	 * Find the latest version. In archive mode multiple versions may exist.
	 */
	default Optional<T> findOneByQualifiedTag(String tag) {
		return findFirstByQualifiedTagOrderByModifiedDesc(tag);
	}

	boolean existsByQualifiedTag(String tag);

	/**
	 * Check the latest version of a tag exists and is newer than the latest version of its deletor.
	 * Only needed in archive mode, where delete notices do not remove older versions.
	 */
	default boolean existsLiveByQualifiedTag(String tag, String deletor) {
		var latest = findOneByQualifiedTag(tag);
		if (latest.isEmpty()) return false;
		var deleted = findOneByQualifiedTag(deletor);
		return deleted.isEmpty() || latest.get().getModified().isAfter(deleted.get().getModified());
	}

	/**
	 * Hard delete: removes all versions of the tag.
	 */
	@Transactional
	void deleteByQualifiedTag(String tag);
}
