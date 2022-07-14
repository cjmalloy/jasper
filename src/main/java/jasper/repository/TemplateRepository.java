package jasper.repository;

import jasper.domain.TagId;
import jasper.domain.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateRepository extends JpaRepository<Template, TagId>, QualifiedTagMixin<Template>, StreamMixin<Template> {

	@Query("""
		FROM Template AS t
		WHERE t.origin = :origin
			AND t.schema IS NOT NULL
			AND (t.tag = ''
				OR t.tag = :tag
				OR locate(concat(t.tag, '/'), :tag) = 1
				OR (:tag LIKE '\\_%' AND locate(concat(t.tag, '/'), :tag) = 2)
				OR (:tag LIKE '+%' AND locate(concat(t.tag, '/'), :tag) = 2))""")
	List<Template> findAllForTagAndOriginWithSchema(String tag, String origin);
}
