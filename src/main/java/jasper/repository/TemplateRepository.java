package jasper.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.TagId;
import jasper.domain.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Abstract methods are profile-specific queries, declared with @Query in
 * {@link TemplateRepositoryRegular} and {@link TemplateRepositoryArchive}.
 */
@NoRepositoryBean
@Transactional(readOnly = true)
public interface TemplateRepository extends JpaRepository<Template, TagId>, QualifiedTagMixin<Template>, StreamMixin<Template>, ModifiedCursor, OriginMixin {

	List<Template> findAllForTagAndOriginWithSchema(String tag, String origin);
	List<Template> findAllForTagAndOriginWithDefaults(String tag, String origin);

	@Modifying
	@Query("""
		UPDATE Template SET
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
		SELECT max(t.modified)
		FROM Template t
		WHERE t.origin = :origin""")
	Instant getCursor(String origin);

	@Query(nativeQuery = true, value = "SELECT DISTINCT origin from template")
	List<String> origins();

	@Modifying(clearAutomatically = true)
	@Query("""
		DELETE FROM Template template
		WHERE template.origin = :origin
			AND template.modified <= :olderThan""")
	void deleteByOriginAndModifiedLessThanEqual(String origin, Instant olderThan);

	/**
	 * The current version if it is not disabled.
	 */
	default Optional<Template> findByTemplateAndOrigin(String template, String origin) {
		return findCurrent(template + origin)
			.filter(t -> t.getConfig() == null || !"true".equals(t.getConfig().path("disabled").asText()));
	}
}
