package ca.hc.jasper.repository;

import ca.hc.jasper.domain.Ref;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefRepository extends JpaRepository<Ref, String> {
}
