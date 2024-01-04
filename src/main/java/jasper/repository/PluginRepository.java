package jasper.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.Plugin;
import jasper.domain.TagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PluginRepository extends JpaRepository<Plugin, TagId>, QualifiedTagMixin<Plugin>, StreamMixin<Plugin>, ModifiedCursor, OriginMixin {

	@Modifying
	@Query("""
		UPDATE Plugin SET
			name = :name,
			config = :config,
			schema = :schema,
			defaults = :defaults,
			generateMetadata = :generateMetadata,
			userUrl = :userUrl,
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
		boolean generateMetadata,
		boolean userUrl,
		Instant modified);

	@Query("""
		SELECT max(p.modified)
		FROM Plugin p
		WHERE p.origin = :origin""")
	Instant getCursor(String origin);

	@Query("""
		FROM Plugin AS p
		WHERE p.origin = :origin
			AND COALESCE(CAST(jsonb_object_field(p.config, 'deleted') as boolean), false) = false
			AND COALESCE(CAST(jsonb_object_field(p.config, 'disabled') as boolean), false) = false
			AND p.tag = :tag""")
	Optional<Plugin> findByTagAndOrigin(String tag, String origin);

	// TODO: Cache this, it rarely changes
	@Query("""
		FROM Plugin AS p
		WHERE p.origin = :origin
			AND COALESCE(CAST(jsonb_object_field(p.config, 'deleted') as boolean), false) = false
			AND COALESCE(CAST(jsonb_object_field(p.config, 'disabled') as boolean), false) = false""")
	List<Plugin> findAllByOrigin(String origin);

	// TODO: Cache this, it rarely changes
	@Query("""
		SELECT p.tag
		FROM Plugin AS p
		WHERE p.origin = :origin
			AND COALESCE(CAST(jsonb_object_field(p.config, 'deleted') as boolean), false) = false
			AND COALESCE(CAST(jsonb_object_field(p.config, 'disabled') as boolean), false) = false
			AND p.generateMetadata = true""")
	List<String> findAllByGenerateMetadataByOrigin(String origin);
}
