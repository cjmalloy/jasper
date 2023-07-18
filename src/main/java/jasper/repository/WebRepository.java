package jasper.repository;

import jasper.domain.Web;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public interface WebRepository extends JpaRepository<Web, String> {
	void deleteAllByDataIsNullAndMimeIsNull();
}
