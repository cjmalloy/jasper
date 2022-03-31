package ca.hc.jasper.repository;

import java.util.Optional;

import ca.hc.jasper.domain.Plugin;
import ca.hc.jasper.domain.TagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PluginRepository extends JpaRepository<Plugin, TagId>, QualifiedTagMixin<Plugin> {
	Optional<Plugin> findByQualifiedTagAndSchemaIsNotNull(String tag);
}
