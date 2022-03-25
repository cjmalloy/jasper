package ca.hc.jasper.repository;

import java.util.List;

import ca.hc.jasper.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueRepository extends JpaRepository<Queue, TagId> {
	List<Queue> findAllByTag(String tag);
}
