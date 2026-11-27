package jasper.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.TagId;
import jasper.domain.Template;
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
public interface TemplateRepository extends JpaRepository<Template, TagId>, QualifiedTagMixin<Template>, StreamMixin<Template>, ModifiedCursor, OriginMixin {

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

	@Query("""
		FROM Template AS t
		WHERE t.origin = :origin
			AND t.schema IS NOT NULL
			AND COALESCE(jsonb_object_field_text(t.config, 'disabled'), '') != 'true'
			AND (t.tag = ''
				OR t.tag = :tag
				OR (:tag LIKE '+%' AND concat('+', t.tag) = :tag)
				OR (:tag LIKE '\\_%' ESCAPE '\\' AND concat('_', t.tag) = :tag)
				OR locate(concat(t.tag, '/'), :tag) = 1
				OR (:tag LIKE '\\_%' ESCAPE '\\' AND locate(concat(t.tag, '/'), :tag) = 2)
				OR (:tag LIKE '+%' AND locate(concat(t.tag, '/'), :tag) = 2))
		ORDER BY tag_levels(t.tag) ASC""")
	List<Template> findAllForTagAndOriginWithSchema(String tag, String origin);

	/**
	 * Archive mode version of {@link #findAllForTagAndOriginWithSchema(String, String)}.
	 * Only the latest version of each template is considered. PostgreSQL only.
	 */
	@Query(nativeQuery = true, value = """
		SELECT t.* FROM (
			SELECT DISTINCT ON (l.tag) l.* FROM template l
			WHERE l.origin = :origin
				AND (l.tag = ''
					OR l.tag = :tag
					OR (:tag LIKE '+%' AND concat('+', l.tag) = :tag)
					OR (:tag LIKE '\\_%' ESCAPE '\\' AND concat('_', l.tag) = :tag)
					OR strpos(:tag, concat(l.tag, '/')) = 1
					OR (:tag LIKE '\\_%' ESCAPE '\\' AND strpos(:tag, concat(l.tag, '/')) = 2)
					OR (:tag LIKE '+%' AND strpos(:tag, concat(l.tag, '/')) = 2))
			ORDER BY l.tag, l.modified DESC) t
		WHERE t.schema IS NOT NULL
			AND COALESCE(t.config->>'disabled', '') != 'true'
		ORDER BY CASE WHEN t.tag = '' THEN 0 ELSE length(t.tag) - length(replace(t.tag, '/', '')) + 1 END ASC""")
	List<Template> findAllLatestForTagAndOriginWithSchema(String tag, String origin);

	default List<Template> findAllForTagAndOriginWithSchema(String tag, String origin, boolean archive) {
		return archive
			? findAllLatestForTagAndOriginWithSchema(tag, origin)
			: findAllForTagAndOriginWithSchema(tag, origin);
	}

	@Query("""
		FROM Template AS t
		WHERE t.origin = :origin
			AND t.defaults IS NOT NULL
			AND COALESCE(jsonb_object_field_text(t.config, 'disabled'), '') != 'true'
			AND (t.tag = ''
				OR t.tag = :tag
				OR (:tag LIKE '+%' AND concat('+', t.tag) = :tag)
				OR (:tag LIKE '\\_%' ESCAPE '\\' AND concat('_', t.tag) = :tag)
				OR locate(concat(t.tag, '/'), :tag) = 1
				OR (:tag LIKE '\\_%' ESCAPE '\\' AND locate(concat(t.tag, '/'), :tag) = 2)
				OR (:tag LIKE '+%' AND locate(concat(t.tag, '/'), :tag) = 2))
		ORDER BY tag_levels(t.tag) ASC""")
	List<Template> findAllForTagAndOriginWithDefaults(String tag, String origin);

	/**
	 * Archive mode version of {@link #findAllForTagAndOriginWithDefaults(String, String)}.
	 * Only the latest version of each template is considered. PostgreSQL only.
	 */
	@Query(nativeQuery = true, value = """
		SELECT t.* FROM (
			SELECT DISTINCT ON (l.tag) l.* FROM template l
			WHERE l.origin = :origin
				AND (l.tag = ''
					OR l.tag = :tag
					OR (:tag LIKE '+%' AND concat('+', l.tag) = :tag)
					OR (:tag LIKE '\\_%' ESCAPE '\\' AND concat('_', l.tag) = :tag)
					OR strpos(:tag, concat(l.tag, '/')) = 1
					OR (:tag LIKE '\\_%' ESCAPE '\\' AND strpos(:tag, concat(l.tag, '/')) = 2)
					OR (:tag LIKE '+%' AND strpos(:tag, concat(l.tag, '/')) = 2))
			ORDER BY l.tag, l.modified DESC) t
		WHERE t.defaults IS NOT NULL
			AND COALESCE(t.config->>'disabled', '') != 'true'
		ORDER BY CASE WHEN t.tag = '' THEN 0 ELSE length(t.tag) - length(replace(t.tag, '/', '')) + 1 END ASC""")
	List<Template> findAllLatestForTagAndOriginWithDefaults(String tag, String origin);

	default List<Template> findAllForTagAndOriginWithDefaults(String tag, String origin, boolean archive) {
		return archive
			? findAllLatestForTagAndOriginWithDefaults(tag, origin)
			: findAllForTagAndOriginWithDefaults(tag, origin);
	}

	/**
	 * Find the latest version if it is not disabled. In archive mode multiple versions may exist.
	 */
	default Optional<Template> findByTemplateAndOrigin(String template, String origin) {
		return findFirstByTagAndOriginOrderByModifiedDesc(template, origin)
			.filter(t -> t.getConfig() == null || !"true".equals(t.getConfig().path("disabled").asText()));
	}
}
