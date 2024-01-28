package jasper.repository;

import jasper.domain.Web;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
@Transactional
public interface WebRepository extends JpaRepository<Web, String> {
	Page<Web> findAllByScrapedAfter(Instant cursor, Pageable page);
	void deleteAllByDataIsNullAndMimeIsNull();
}
