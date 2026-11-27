package jasper.repository;

import jasper.domain.External;
import jasper.domain.TagId;
import jasper.domain.User;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static jasper.domain.proj.Tag.localTag;
import static jasper.domain.proj.Tag.tagOrigin;

/**
 * Replaced by {@link UserRepositoryArchive} in archive mode.
 */
@Repository
@Profile("!archive")
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
			AND jsonb_exists(jsonb_object_field(u.external, 'ids'), :externalId)
		ORDER BY collate_c(u.tag)""")
	List<User> findAllByOriginAndExternalId(String origin, String externalId);

	@Query("""
		SELECT u FROM User u
		WHERE u.tag IN (concat('+', :tag), concat('_', :tag))
			AND u.origin = :origin
		ORDER BY collate_c(u.tag)""")
	List<User> findAllByTagSuffixAndOrigin(String tag, String origin);

	/**
	 * Both the public and protected user for a qualified tag without the prefix, ie. "user/alice@origin".
	 */
	default List<User> findAllByQualifiedSuffix(String qualifiedSuffix) {
		return findAllByTagSuffixAndOrigin(localTag(qualifiedSuffix), tagOrigin(qualifiedSuffix));
	}

	@Modifying
	@Transactional
	@Query("""
		UPDATE User u
		SET u.external = jsonb_set(
			COALESCE(u.external, cast_to_jsonb('{}')),
			'{ids}',
			jsonb_array_append(COALESCE(jsonb_object_field(u.external, 'ids'), cast_to_jsonb('[]')), :externalId),
			true
		)
		WHERE u.tag = :tag
			AND u.origin = :origin
			AND NOT COALESCE(jsonb_exists(jsonb_object_field(u.external, 'ids'), :externalId), false)""")
	int setExternalId(String tag, String origin, String externalId);
}
