package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom repository implementation for User queries that require database-specific SQL.
 */
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

	@PersistenceContext
	private EntityManager em;

	@Value("${spring.jpa.database-platform:}")
	private String databasePlatform;

	private boolean isSqlite() {
		return databasePlatform.contains("SQLite");
	}

	@Override
	@Transactional
	public int setExternalId(String tag, String origin, String externalId) {
		String sql;
		if (isSqlite()) {
			sql = """
				UPDATE users
				SET external = json_set(
					COALESCE(external, json('{}')),
					'$.ids',
					json_insert(
						COALESCE(json_extract(external, '$.ids'), json('[]')),
						'$[#]',
						:externalId
					)
				)
				WHERE tag = :tag
					AND origin = :origin
					AND NOT COALESCE(jsonb_exists(json_extract(COALESCE(external, json('{}')), '$.ids'), :externalId), 0)
				""";
		} else {
			sql = """
				UPDATE users users
				SET external = jsonb_set(
					COALESCE(users.external, '{}'::jsonb),
					'{ids}',
					COALESCE(users.external->'ids', '[]'::jsonb) || to_jsonb(CAST(:externalId as text)),
					true)
				WHERE users.tag = :tag
					AND users.origin = :origin
					AND NOT COALESCE(jsonb_exists(users.external->'ids', :externalId), false)
				""";
		}
		return em.createNativeQuery(sql)
			.setParameter("tag", tag)
			.setParameter("origin", origin)
			.setParameter("externalId", externalId)
			.executeUpdate();
	}
}
