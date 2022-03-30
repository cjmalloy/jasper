package ca.hc.jasper.repository;

import ca.hc.jasper.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends JpaRepository<Tag, TagId>, QualifiedTagMixin<Tag> {
}
