package ca.hc.jasper.repository;

import ca.hc.jasper.domain.TagId;
import ca.hc.jasper.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends TagOriginMixin<User>, JpaRepository<User, TagId> {
}
