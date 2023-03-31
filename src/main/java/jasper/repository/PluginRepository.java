package jasper.repository;

import jasper.domain.Plugin;
import jasper.domain.TagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PluginRepository extends JpaRepository<Plugin, TagId>, QualifiedTagMixin<Plugin>, StreamMixin<Plugin>, ModifiedCursor, OriginMixin {

	@Query(value = """
		SELECT max(p.modified)
		FROM Plugin p
		WHERE p.origin = :origin""")
	Instant getCursor(String origin);

	@Query("""
		FROM Plugin AS p
		WHERE p.origin = :origin
			AND p.schema IS NOT NULL
			AND p.tag = :tag""")
	Optional<Plugin> findByTagAndOriginWithSchema(String tag, String origin);

	// TODO: Cache this, it rarely changes
	@Query("""
		SELECT p.tag
		FROM Plugin AS p
		WHERE p.origin = :origin
			AND p.generateMetadata = true""")
	List<String> findAllByGenerateMetadataByOrigin(String origin);
}
