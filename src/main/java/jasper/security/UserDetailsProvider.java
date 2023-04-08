package jasper.security;

import jasper.domain.User;

import java.util.Optional;

public interface UserDetailsProvider {
	Optional<User> findOneByQualifiedTag(String tag);
}
