package jasper.repository;

import jasper.domain.TagId;
import jasper.domain.User;
import jasper.security.UserDetailsProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface UserRepository extends JpaRepository<User, TagId>, QualifiedTagMixin<User>, StreamMixin<User>,
	ModifiedCursor, OriginMixin, UserDetailsProvider {

	@Query(value = """
		SELECT max(u.modified)
		FROM User u
		WHERE u.origin = :origin""")
	Instant getCursor(String origin);
}
