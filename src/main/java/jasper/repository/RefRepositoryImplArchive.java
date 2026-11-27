package jasper.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * Archive mode plugin tag queries. Archive mode is only supported on PostgreSQL.
 */
@Repository
@Profile("archive & !sqlite")
public class RefRepositoryImplArchive extends RefRepositoryImplPostgres {

	/**
	 * Only the latest version is counted.
	 */
	@Override
	protected String versionFilter() {
		return " AND NOT EXISTS (SELECT 1 FROM ref n WHERE n.url = r.url AND n.origin = r.origin AND n.modified > r.modified)";
	}
}
