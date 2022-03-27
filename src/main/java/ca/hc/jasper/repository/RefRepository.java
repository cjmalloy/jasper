package ca.hc.jasper.repository;

import java.util.Optional;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.domain.RefId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RefRepository extends JpaRepository<Ref, RefId>, JpaSpecificationExecutor<Ref> {
	Optional<Ref> findOneByUrlAndOrigin(String url, String origin);
	void deleteByUrlAndOrigin(String url, String origin);
	boolean existsByUrlAndOrigin(String url, String origin);
}
