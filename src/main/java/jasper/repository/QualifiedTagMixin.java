package jasper.repository;

import jasper.domain.proj.Tag;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

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

	@Transactional
	void deleteByQualifiedTag(String tag);

	/**
	 * Remove every version modified at or before olderThan. Only used to prune in archive mode.
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		DELETE FROM #{#entityName} t
		WHERE t.qualifiedTag = :tag
			AND t.modified <= :olderThan""")
	void deleteByQualifiedTagAndModifiedLessThanEqual(String tag, Instant olderThan);
}
