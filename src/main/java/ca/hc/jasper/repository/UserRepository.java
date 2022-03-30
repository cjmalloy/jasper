package ca.hc.jasper.repository;

import java.util.Optional;

import ca.hc.jasper.domain.TagId;
import ca.hc.jasper.domain.User;
import ca.hc.jasper.domain.proj.LastNotified;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, TagId>, QualifiedTagMixin<User> {
	Optional<LastNotified> getLastNotifiedByTagAndOrigin(String tag, String origin);
}
