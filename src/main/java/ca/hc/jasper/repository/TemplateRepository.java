package ca.hc.jasper.repository;

import java.util.List;
import java.util.Optional;

import ca.hc.jasper.domain.TagId;
import ca.hc.jasper.domain.Template;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRepository extends JpaRepository<Template, TagId>, JpaSpecificationExecutor<Template> {

	@Query("""
		FROM Template AS t
		WHERE t.origin = :origin
			AND t.schema IS NOT NULL
			AND (t.prefix = ''
				OR locate(concat(t.prefix, '/'), :tag) = 1
				OR (:tag LIKE '\\_%' AND locate(concat(t.prefix, '/'), :tag) = 2))""")
	List<Template> findAllForTagAndOriginWithSchema(String tag, String origin);
	Optional<Template> findOneByQualifiedPrefix(String prefix);
	void deleteByQualifiedPrefix(String prefix);
	boolean existsByQualifiedPrefix(String prefix);
}
