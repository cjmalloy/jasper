package ca.hc.jasper.repository;

import java.util.List;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.domain.RefId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefRepository extends JpaRepository<Ref, RefId> {
	List<Ref> findAllByUrl(String url);
}
