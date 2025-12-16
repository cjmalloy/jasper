package jasper.repository;

import jasper.domain.External;
import jasper.domain.TagId;
import jasper.domain.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
@Transactional(readOnly = true)
public interface UserRepository extends JpaRepository<User, TagId>, QualifiedTagMixin<User>, StreamMixin<User>,
	ModifiedCursor, OriginMixin {

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
			pubKey = :pubKey,
			authorizedKeys = :authorizedKeys,
			external = :external
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
		List<String> readAccess,
		List<String> writeAccess,
		List<String> tagReadAccess,
		List<String> tagWriteAccess,
		Instant modified,
		byte[] key,
		byte[] pubKey,
		String authorizedKeys,
		External external);

	@Query("""
		SELECT max(u.modified)
		FROM User u
		WHERE u.origin = :origin""")
	Instant getCursor(String origin);

	@Query(nativeQuery = true, value = "SELECT DISTINCT origin from users")
	List<String> origins();

	@Modifying(clearAutomatically = true)
	@Query("""
		DELETE FROM User u
		WHERE u.origin = :origin
			AND u.modified <= :olderThan""")
	void deleteByOriginAndModifiedLessThanEqual(String origin, Instant olderThan);

	@Query("""
		SELECT u FROM User u
		WHERE u.origin = :origin
			AND jsonb_exists(jsonb_extract_path(u.external, 'ids'), :externalId)""")
	List<User> findAllByOriginAndExternalId(String origin, String externalId, Sort sort);

	@Query("""
		SELECT u FROM User u
		WHERE (u.qualifiedTag = '+' || :tag) OR (u.qualifiedTag = '_' || :tag)""")
	List<User> findAllByQualifiedSuffix(String tag, Sort sort);

	// TODO: Sync cache
	@Modifying
	@Transactional
	@Query(nativeQuery = true, value = """
		UPDATE users users
		SET external = jsonb_set(
				COALESCE(users.external, '{}'::jsonb),
				'{ids}',
				COALESCE(users.external->'ids', '[]'::jsonb) || to_jsonb(CAST(:externalId as text)),
				true)
		WHERE users.tag = :tag
			AND users.origin = :origin
			AND NOT COALESCE(jsonb_exists(users.external->'ids', :externalId), false)""")
	int setExternalId(String tag, String origin, String externalId);
}
