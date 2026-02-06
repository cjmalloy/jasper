package jasper.repository;

import jasper.domain.Plugin;
import jasper.domain.TagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public interface PluginRepository extends JpaRepository<Plugin, TagId>, QualifiedTagMixin<Plugin>, StreamMixin<Plugin>, ModifiedCursor, OriginMixin {

	@Modifying(clearAutomatically = true)
	@Query(value = """
		UPDATE plugin SET
			name = :name,
			config = CAST(:config AS jsonb),
			schema = CAST(:schema AS jsonb),
			defaults = CAST(:defaults AS jsonb),
			modified = :modified
		WHERE
			tag = :tag AND
			origin = :origin AND
			modified = :cursor""", nativeQuery = true)
	int optimisticUpdate(
		Instant cursor,
		String tag,
		String origin,
		String name,
		String config,
		String schema,
		String defaults,
		Instant modified);

	@Query("""
		SELECT max(p.modified)
		FROM Plugin p
		WHERE p.origin = :origin""")
	Instant getCursor(String origin);

	@Query(nativeQuery = true, value = "SELECT DISTINCT origin from plugin")
	List<String> origins();

	@Modifying(clearAutomatically = true)
	@Query("""
		DELETE FROM Plugin plugin
		WHERE plugin.origin = :origin
			AND plugin.modified <= :olderThan""")
	void deleteByOriginAndModifiedLessThanEqual(String origin, Instant olderThan);

	@Query("""
		FROM Plugin AS p
		WHERE p.origin = :origin
			AND COALESCE(CAST(jsonb_object_field(p.config, 'disabled') as boolean), false) = false
			AND p.tag = :tag""")
	Optional<Plugin> findByTagAndOrigin(String tag, String origin);
}
