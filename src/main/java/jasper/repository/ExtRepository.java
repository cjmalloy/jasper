package jasper.repository;

import jasper.domain.Ext;
import jasper.domain.TagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExtRepository extends JpaRepository<Ext, TagId>, QualifiedTagMixin<Ext>, StreamMixin<Ext> {
}
