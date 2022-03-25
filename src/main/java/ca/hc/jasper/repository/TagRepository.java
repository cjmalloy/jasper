package ca.hc.jasper.repository;

import java.util.List;

import ca.hc.jasper.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, TagId> {
	List<Tag> findAllByTag(String tag);
}
