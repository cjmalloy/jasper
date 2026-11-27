package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jasper.domain.Template;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("!archive")
public class TemplateRepositoryImpl implements TemplateRepositoryCustom {

	@PersistenceContext
	EntityManager em;

	@Override
	public List<Template> findAllForTagAndOriginWithSchema(String tag, String origin) {
		return em.createQuery("""
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
			ORDER BY tag_levels(t.tag) ASC""", Template.class)
			.setParameter("tag", tag)
			.setParameter("origin", origin)
			.getResultList();
	}

	@Override
	public List<Template> findAllForTagAndOriginWithDefaults(String tag, String origin) {
		return em.createQuery("""
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
			ORDER BY tag_levels(t.tag) ASC""", Template.class)
			.setParameter("tag", tag)
			.setParameter("origin", origin)
			.getResultList();
	}
}
