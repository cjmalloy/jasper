package ca.hc.jasper.repository;

import java.util.List;

import ca.hc.jasper.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends JpaRepository<Template, TagId>, QualifiedTagMixin<Template> {

	@Query(nativeQuery = true, value = """
		SELECT * FROM template
		WHERE template.origin = :origin
			AND template.schema IS NOT NULL
			AND (position(template.tag||'/' in :tag) = 1
				OR (:tag LIKE '\\_%' AND position(template.tag||'/' in :tag) = 2))""")
	List<Template> findAllForTagAndOriginWithSchema(String tag, String origin);
}
