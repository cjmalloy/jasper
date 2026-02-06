package jasper.repository;

import jasper.domain.Ext;
import jasper.domain.TagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
@Transactional(readOnly = true)
public interface ExtRepository extends JpaRepository<Ext, TagId>, QualifiedTagMixin<Ext>, StreamMixin<Ext>, ModifiedCursor, OriginMixin {

	@Modifying(clearAutomatically = true)
	@Query(value = """
		UPDATE ext SET
			name = :name,
			config = CAST(:config AS jsonb),
			modified = :modified
		WHERE
			tag = :tag AND
			origin = :origin AND
			modified = :cursor""", nativeQuery = true)
	int optimisticUpdate(
		Instant cursor,
		String tag,
		String origin,
		String name,
		String config,
		Instant modified);

	@Query("""
		SELECT max(e.modified)
		FROM Ext e
		WHERE e.origin = :origin""")
	Instant getCursor(String origin);

	@Query(nativeQuery = true, value = "SELECT DISTINCT origin from ext")
	List<String> origins();

	@Modifying(clearAutomatically = true)
	@Query("""
		DELETE FROM Ext ext
		WHERE ext.origin = :origin
			AND ext.modified <= :olderThan""")
	void deleteByOriginAndModifiedLessThanEqual(String origin, Instant olderThan);
}
