package ca.hc.jasper.repository;

import ca.hc.jasper.domain.Tag;
import ca.hc.jasper.domain.TagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends TagOriginMixin<Tag>, JpaRepository<Tag, TagId> {
}
