package jasper.repository;

import jasper.domain.Template;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("!archive")
@Transactional(readOnly = true)
public interface TemplateRepositoryRegular extends TemplateRepository {

	@Override
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

	@Override
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
}
