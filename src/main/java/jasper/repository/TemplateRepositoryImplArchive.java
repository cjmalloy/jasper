package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jasper.domain.Template;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Archive mode Template queries. Multiple versions of a Template may exist.
 * Archive mode is only supported on PostgreSQL.
 */
@Repository("templateRepositoryImpl")
@Profile("archive & !sqlite")
public class TemplateRepositoryImplArchive implements TemplateRepositoryCustom {

	@PersistenceContext
	EntityManager em;

	/**
	 * Only the latest version of each template is considered.
	 */
	@Override
	public List<Template> findAllForTagAndOriginWithSchema(String tag, String origin) {
		return em.createNativeQuery("""
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
		ORDER BY CASE WHEN t.tag = '' THEN 0 ELSE length(t.tag) - length(replace(t.tag, '/', '')) + 1 END ASC""", Template.class)
			.setParameter("tag", tag)
			.setParameter("origin", origin)
			.getResultList();
	}

	/**
	 * Only the latest version of each template is considered.
	 */
	@Override
	public List<Template> findAllForTagAndOriginWithDefaults(String tag, String origin) {
		return em.createNativeQuery("""
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
		ORDER BY CASE WHEN t.tag = '' THEN 0 ELSE length(t.tag) - length(replace(t.tag, '/', '')) + 1 END ASC""", Template.class)
			.setParameter("tag", tag)
			.setParameter("origin", origin)
			.getResultList();
	}
}
