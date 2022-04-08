package ca.hc.jasper.repository;

import java.util.List;

import ca.hc.jasper.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PluginRepository extends JpaRepository<Plugin, TagId>, QualifiedTagMixin<Plugin> {

	@Query("""
		FROM Plugin AS p
		WHERE p.origin = :origin
			AND p.schema IS NOT NULL
			AND (p.tag = ''
				OR p.tag = :tag
				OR locate(concat(p.tag, '/'), :tag) = 1
				OR (:tag LIKE '\\_%' AND locate(concat(p.tag, '/'), :tag) = 2))""")
	List<Plugin> findAllForTagAndOriginWithSchema(String tag, String origin);

	@Query("""
		SELECT p.tag
		FROM Plugin AS p
		WHERE p.generateMetadata = true""")
	List<String> findAllByGenerateMetadata();
}
