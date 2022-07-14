package jasper.repository;

import jasper.domain.Origin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OriginRepository extends JpaRepository<Origin, String>, JpaSpecificationExecutor<Origin>, StreamMixin<Origin> {
}
