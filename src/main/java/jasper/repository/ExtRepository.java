package jasper.repository;

import jasper.domain.Ext;
import jasper.domain.TagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ExtRepository extends JpaRepository<Ext, TagId>, QualifiedTagMixin<Ext>, StreamMixin<Ext>, ModifiedCursor {

	@Query(value = """
		SELECT max(e.modified)
		FROM Ext e
		WHERE e.origin = :origin""")
	Instant getCursor(String origin);
}
