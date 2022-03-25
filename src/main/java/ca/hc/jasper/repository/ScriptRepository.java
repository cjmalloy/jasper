package ca.hc.jasper.repository;

import ca.hc.jasper.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptRepository extends JpaRepository<Script, String> {
}
