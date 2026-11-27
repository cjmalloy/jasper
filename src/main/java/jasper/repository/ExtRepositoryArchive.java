package jasper.repository;

import jasper.domain.Ext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static jasper.util.Archive.isBlank;

/**
 * Archive mode Ext repository. Replaces {@link ExtRepository}, which is only a bean without the archive profile.
 */
@Repository
@Profile("archive")
@Transactional(readOnly = true)
public interface ExtRepositoryArchive extends ExtRepository {

	/**
	 * A blank current version is a tombstone and is treated as missing.
	 */
	@Override
	default Optional<Ext> findCurrent(String qualifiedTag) {
		return findOneByQualifiedTag(qualifiedTag).filter(e -> !isBlank(e));
	}

	@Override
	default boolean existsCurrent(String qualifiedTag) {
		return findCurrent(qualifiedTag).isPresent();
	}
}
