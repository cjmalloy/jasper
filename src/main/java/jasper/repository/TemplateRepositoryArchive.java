package jasper.repository;

import jasper.domain.Template;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

import static jasper.util.Archive.isBlank;

/**
 * Archive mode Template queries. Multiple versions of a Template may exist.
 * Archive mode is only supported on PostgreSQL.
 */
@Repository
@Profile("archive & !sqlite")
@Transactional(readOnly = true)
public interface TemplateRepositoryArchive extends TemplateRepository {

	/**
	 * Only the latest version of each template is considered.
	 */
	@Override
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
	List<Template> findAllForTagAndOriginWithSchema(String tag, String origin);

	/**
	 * Only the latest version of each template is considered.
	 */
	@Override
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
	List<Template> findAllForTagAndOriginWithDefaults(String tag, String origin);

	/**
	 * A blank current version is a tombstone and is treated as missing.
	 */
	@Override
	default Optional<Template> findCurrent(String qualifiedTag) {
		return findOneByQualifiedTag(qualifiedTag).filter(t -> !isBlank(t));
	}

	@Override
	default boolean existsCurrent(String qualifiedTag) {
		return findCurrent(qualifiedTag).isPresent();
	}
}
