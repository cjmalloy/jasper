package ca.hc.jasper.repository;

import java.util.List;

import ca.hc.jasper.domain.Template;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends JpaRepository<Template, String>, JpaSpecificationExecutor<Template> {

	@Query(nativeQuery = true, value = """
		SELECT * FROM template
		WHERE template.schema IS NOT NULL
			AND position(template.tag in tag) = 1""")
	List<Template> findAllForTagWithSchema(String tag);
}
