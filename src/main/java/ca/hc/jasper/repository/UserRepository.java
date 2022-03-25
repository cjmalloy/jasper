package ca.hc.jasper.repository;

import java.util.List;

import ca.hc.jasper.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, TagId> {
	List<User> findAllByTag(String tag);
}
