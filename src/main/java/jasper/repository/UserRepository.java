package jasper.repository;

import com.fasterxml.jackson.databind.node.ArrayNode;
import jasper.domain.TagId;
import jasper.domain.User;
import jasper.security.UserDetailsProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, TagId>, QualifiedTagMixin<User>, StreamMixin<User>,
	ModifiedCursor, OriginMixin, UserDetailsProvider {

	@Modifying
	@Query("""
		UPDATE User SET
			name = :name,
			role = :role,
			readAccess = :readAccess,
			writeAccess = :writeAccess,
			tagReadAccess = :tagReadAccess,
			tagWriteAccess = :tagWriteAccess,
			modified = :modified,
			key = :key,
			pubKey = :pubKey
		WHERE
			tag = :tag AND
			origin = :origin AND
			modified = :cursor""")
	int optimisticUpdate(
		Instant cursor,
		String tag,
		String origin,
		String name,
		String role,
		ArrayNode readAccess,
		ArrayNode writeAccess,
		ArrayNode tagReadAccess,
		ArrayNode tagWriteAccess,
		Instant modified,
		byte[] key,
		byte[] pubKey);

	@Query("""
		SELECT max(u.modified)
		FROM User u
		WHERE u.origin = :origin""")
	Instant getCursor(String origin);

	@Modifying(clearAutomatically = true)
	@Query("""
		DELETE FROM User user
		WHERE user.origin = :origin
			AND user.modified <= :olderThan""")
	void deleteByOriginAndModifiedLessThanEqual(String origin, Instant olderThan);

	List<User> findAllByOriginAndPubKeyIsNotNull(String origin);
}
