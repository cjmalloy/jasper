package jasper.repository;

import jasper.domain.TagId;
import jasper.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, TagId>, QualifiedTagMixin<User>, StreamMixin<User> {
}
