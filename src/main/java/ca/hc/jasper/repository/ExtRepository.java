package ca.hc.jasper.repository;

import ca.hc.jasper.domain.Ext;
import ca.hc.jasper.domain.TagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtRepository extends JpaRepository<Ext, TagId>, QualifiedTagMixin<Ext> {
}
