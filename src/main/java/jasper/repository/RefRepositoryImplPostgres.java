package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jasper.domain.RefId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@Profile("!sqlite & !archive")
public class RefRepositoryImplPostgres implements RefRepositoryCustom {

	@PersistenceContext
	EntityManager em;

	@Autowired
	RefRepository refRepository;

	/**
	 * Extra filter on the counted responses.
	 */
	protected String versionFilter() {
		return "";
	}

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
				AND (r.url ~ '^tag:/[_+]?user([/?]|$)'
					OR COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true')%s
				AND %s
			GROUP BY t.tag
			""".formatted(distinct, versionFilter(), originFilter), Object[].class)
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

	@Override
	public List<RefId> findAllResponseIdsWithTag(String url, String origin, String tag) {
		return refRepository.findAllResponseIdsWithTag(url, origin, tag);
	}

	@Override
	public int updateObsolete(String url, String rootOrigin, boolean pinned) {
		return refRepository.updateObsolete(url, rootOrigin, pinned);
	}

	@Override
	public boolean higherRankExists(String url, String origin, String rootOrigin, boolean pinned, int rank, Instant modified) {
		return refRepository.higherRankExists(url, rootOrigin, pinned, rank, modified);
	}
}
