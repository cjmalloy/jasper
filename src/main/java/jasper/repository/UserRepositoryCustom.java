package jasper.repository;

/**
 * Custom repository fragment for User queries that require database-specific SQL.
 */
public interface UserRepositoryCustom {

	int setExternalId(String tag, String origin, String externalId);
}
