package jasper.repository;

import jasper.domain.Plugin;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static jasper.util.Archive.isBlank;

/**
 * Archive mode Plugin repository. Replaces {@link PluginRepository}, which is only a bean without the archive profile.
 */
@Repository
@Profile("archive")
@Transactional(readOnly = true)
public interface PluginRepositoryArchive extends PluginRepository {

	/**
	 * A blank current version is a tombstone and is treated as missing.
	 */
	@Override
	default Optional<Plugin> findCurrent(String qualifiedTag) {
		return findOneByQualifiedTag(qualifiedTag).filter(p -> !isBlank(p));
	}

	@Override
	default boolean existsCurrent(String qualifiedTag) {
		return findCurrent(qualifiedTag).isPresent();
	}
}
