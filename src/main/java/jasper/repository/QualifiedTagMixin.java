package jasper.repository;

import jasper.domain.proj.Tag;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static jasper.domain.proj.Tag.localTag;
import static jasper.domain.proj.Tag.tagOrigin;

@Transactional(readOnly = true)
public interface QualifiedTagMixin<T extends Tag> extends JpaSpecificationExecutor<T> {
	Optional<T> findFirstByTagAndOriginOrderByModifiedDesc(String tag, String origin);

	/**
	 * Find the latest version. In archive mode multiple versions may exist.
	 * Matches on the primary key columns instead of the unindexed qualifiedTag formula.
	 */
	default Optional<T> findOneByQualifiedTag(String qualifiedTag) {
		return findFirstByTagAndOriginOrderByModifiedDesc(localTag(qualifiedTag), tagOrigin(qualifiedTag));
	}

	/**
	 * The current version, or empty if it is missing.
	 * Archive repositories override this to treat a tombstone as missing.
	 */
	default Optional<T> findCurrent(String qualifiedTag) {
		return findOneByQualifiedTag(qualifiedTag);
	}

	/**
	 * Whether a current version exists.
	 * Archive repositories override this to treat a tombstone as missing.
	 */
	default boolean existsCurrent(String qualifiedTag) {
		return existsByQualifiedTag(qualifiedTag);
	}

	boolean existsByTagAndOrigin(String tag, String origin);

	default boolean existsByQualifiedTag(String qualifiedTag) {
		return existsByTagAndOrigin(localTag(qualifiedTag), tagOrigin(qualifiedTag));
	}

	@Transactional
	void deleteByTagAndOrigin(String tag, String origin);

	@Transactional
	default void deleteByQualifiedTag(String qualifiedTag) {
		deleteByTagAndOrigin(localTag(qualifiedTag), tagOrigin(qualifiedTag));
	}

	/**
	 * Remove every version modified at or before olderThan. Only used to prune in archive mode.
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		DELETE FROM #{#entityName} t
		WHERE t.tag = :tag
			AND t.origin = :origin
			AND t.modified <= :olderThan""")
	void deleteByTagAndOriginAndModifiedLessThanEqual(String tag, String origin, Instant olderThan);

	@Transactional
	default void deleteByQualifiedTagAndModifiedLessThanEqual(String qualifiedTag, Instant olderThan) {
		deleteByTagAndOriginAndModifiedLessThanEqual(localTag(qualifiedTag), tagOrigin(qualifiedTag), olderThan);
	}
}
