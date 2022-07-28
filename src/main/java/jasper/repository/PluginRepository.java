package jasper.repository;

import jasper.domain.Plugin;
import jasper.domain.TagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PluginRepository extends JpaRepository<Plugin, TagId>, QualifiedTagMixin<Plugin>, StreamMixin<Plugin>, ModifiedCursor {

	@Query(value = """
		SELECT max(p.modified)
		FROM Plugin p
		WHERE p.origin = :origin""")
	Instant getCursor(String origin);

	@Query("""
		FROM Plugin AS p
		WHERE p.origin = :origin
			AND p.schema IS NOT NULL
			AND (p.tag = ''
				OR p.tag = :tag
				OR locate(concat(p.tag, '/'), :tag) = 1
				OR (:tag LIKE '\\_%' AND locate(concat(p.tag, '/'), :tag) = 2)
				OR (:tag LIKE '+%' AND locate(concat(p.tag, '/'), :tag) = 2))""")
	List<Plugin> findAllForTagAndOriginWithSchema(String tag, String origin);

	// TODO: Cache this, it rarely changes
	@Query("""
		SELECT p.tag
		FROM Plugin AS p
		WHERE p.origin = :origin
			AND p.generateMetadata = true""")
	List<String> findAllByGenerateMetadataByOrigin(String origin);
}
