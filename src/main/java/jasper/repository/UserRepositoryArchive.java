package jasper.repository;

import jasper.domain.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static jasper.util.Archive.isBlank;

/**
 * Archive mode User repository. Replaces {@link UserRepository}, which is only a bean without the archive profile.
 */
@Repository
@Profile("archive")
@Transactional(readOnly = true)
public interface UserRepositoryArchive extends UserRepository {

	/**
	 * A blank current version is a tombstone and is treated as missing.
	 */
	@Override
	default Optional<User> findCurrent(String qualifiedTag) {
		return findOneByQualifiedTag(qualifiedTag).filter(u -> !isBlank(u));
	}

	@Override
	default boolean existsCurrent(String qualifiedTag) {
		return findCurrent(qualifiedTag).isPresent();
	}
}
