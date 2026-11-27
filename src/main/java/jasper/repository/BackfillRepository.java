package jasper.repository;

import jasper.domain.Ref;
import jasper.domain.RefId;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Database-specific metadata backfill (bulk UPDATE with CTEs/JSON aggregation).
 * Declared with @Query in profile-specific sub-interfaces.
 */
@NoRepositoryBean
public interface BackfillRepository extends Repository<Ref, RefId> {
	/**
	 * @param pinned if plugin/pinned is installed in the origin, so pinned Refs rank higher
	 */
	int backfillMetadata(String origin, boolean pinned, int batchSize);
}
