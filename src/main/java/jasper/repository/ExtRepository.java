package jasper.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.Ext;
import jasper.domain.Metadata;
import jasper.domain.TagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ExtRepository extends JpaRepository<Ext, TagId>, QualifiedTagMixin<Ext>, StreamMixin<Ext>, ModifiedCursor, OriginMixin {

	@Modifying
	@Query("""
		UPDATE Ext SET
			name = :name,
			config = :config,
			modified = :modified
		WHERE
			tag = :tag AND
			origin = :origin AND
			modified = :cursor""")
	int optimisticUpdate(
		Instant cursor,
		String tag,
		String origin,
		String name,
		JsonNode config,
		Instant modified);

	@Query(value = """
		SELECT max(e.modified)
		FROM Ext e
		WHERE e.origin = :origin""")
	Instant getCursor(String origin);
}
