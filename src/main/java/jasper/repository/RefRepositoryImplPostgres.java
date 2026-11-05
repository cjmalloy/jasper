package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Profile("!sqlite")
public class RefRepositoryImplPostgres implements RefRepositoryCustom {

	@PersistenceContext
	private EntityManager em;

	@Override
	public List<String> findAllPluginTagsInResponses(String url, String origin) {
		return em.createNativeQuery("""
			SELECT DISTINCT t.tag
			FROM ref r
				CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(r.metadata->'expandedTags', r.tags)) AS t(tag)
			WHERE r.url != :url
				AND r.sources @> jsonb_build_array(:url)
				AND t.tag ~ '^[_+]?plugin(/|$)'
				AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))
			""", String.class)
			.setParameter("url", url)
			.setParameter("origin", origin)
			.getResultList();
	}

	@Override
	public List<Object[]> countPluginTagsInResponses(String url, String origin) {
		return countPluginTags(url, origin, "(r.url, r.origin)", "(:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))");
	}

	@Override
	public List<Object[]> countLocalPluginTagsInResponses(String url, String origin) {
		return countPluginTags(url, origin, "r.url", "r.origin = :origin");
	}

	private List<Object[]> countPluginTags(String url, String origin, String distinct, String originFilter) {
		return em.createNativeQuery("""
			SELECT t.tag, COUNT(DISTINCT %s)
			FROM ref r
				CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(r.metadata->'expandedTags', r.tags)) AS t(tag)
			WHERE r.url != :url
				AND r.sources @> jsonb_build_array(:url)
				AND t.tag ~ '^[_+]?plugin(/|$)'
				AND (r.url ~ '^tag:/[_+]?user([/?]|$)' OR COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true')
				AND %s
			GROUP BY t.tag
			""".formatted(distinct, originFilter), Object[].class)
			.setParameter("url", url)
			.setParameter("origin", origin)
			.getResultList();
	}

	@Override
	public List<String> findAllUserPluginTagsInResponses(String url, String origin) {
		return em.createNativeQuery("""
			SELECT DISTINCT t.tag
			FROM ref r
				CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(r.metadata->'expandedTags', r.tags)) AS t(tag)
			WHERE r.url != :url
				AND r.sources @> jsonb_build_array(:url)
				AND t.tag ~ '^[_+]?plugin/user(/|$)'
				AND r.origin = :origin
			""", String.class)
			.setParameter("url", url)
			.setParameter("origin", origin)
			.getResultList();
	}
}
