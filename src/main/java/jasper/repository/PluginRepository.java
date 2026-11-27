package jasper.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.Plugin;
import jasper.domain.TagId;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Replaced by {@link PluginRepositoryArchive} in archive mode.
 */
@Repository
@Profile("!archive")
@Transactional(readOnly = true)
public interface PluginRepository extends JpaRepository<Plugin, TagId>, QualifiedTagMixin<Plugin>, StreamMixin<Plugin>, ModifiedCursor, OriginMixin {

	@Modifying
	@Query("""
		UPDATE Plugin SET
			name = :name,
			config = :config,
			schema = :schema,
			defaults = :defaults,
			modified = :modified
		WHERE
			tag = :tag AND
			origin = :origin AND
			modified = :cursor""")
	int optimisticUpdate(
		Instant cursor,
		String tag,
		String origin,
		String name,
		JsonNode config,
		ObjectNode schema,
		JsonNode defaults,
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

	/**
	 * The current version if it is not disabled.
	 */
	default Optional<Plugin> findByTagAndOrigin(String tag, String origin) {
		return findCurrent(tag + origin)
			.filter(p -> p.getConfig() == null || !"true".equals(p.getConfig().path("disabled").asText()));
	}
}
