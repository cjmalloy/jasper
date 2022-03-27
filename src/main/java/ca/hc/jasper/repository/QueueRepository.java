package ca.hc.jasper.repository;

import ca.hc.jasper.domain.Queue;
import ca.hc.jasper.domain.TagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueueRepository extends TagOriginMixin<Queue>, JpaRepository<Queue, TagId> {
}
