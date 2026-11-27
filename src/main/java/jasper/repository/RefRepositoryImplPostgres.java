package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.domain.RefId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
@Profile("!sqlite & !archive")
public class RefRepositoryImplPostgres implements RefRepositoryCustom {

	@PersistenceContext
	EntityManager em;

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
					OR COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true')
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

	@Override
	public List<RefId> findAllResponseIdsWithTag(String url, String origin, String tag) {
		return em.createQuery("""
			SELECT new jasper.domain.RefId(r.url, r.origin) FROM Ref r
			WHERE r.url != :url
				AND jsonb_exists(r.sources, :url) = true
				AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags), :tag) = true
				AND (r.url IN ('tag:/user', 'tag:/+user', 'tag:/_user')
					OR r.url LIKE 'tag:/user/%' OR r.url LIKE 'tag:/user?%'
					OR r.url LIKE 'tag:/+user/%' OR r.url LIKE 'tag:/+user?%'
					OR r.url LIKE 'tag:/\\_user/%' ESCAPE '\\' OR r.url LIKE 'tag:/\\_user?%' ESCAPE '\\'
					OR COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true')
				AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""", RefId.class)
			.setParameter("url", url)
			.setParameter("origin", origin)
			.setParameter("tag", tag)
			.getResultList();
	}

	@Override
	@Transactional
	public int updateObsolete(String url, String rootOrigin, boolean pinned) {
		return em.createQuery("""
			UPDATE Ref r
			SET r.metadata = jsonb_set(
				coalesce(r.metadata, cast_to_jsonb('{}')),
				'{obsolete}',
				CASE
					WHEN EXISTS (
						SELECT 1 FROM Ref r2
						WHERE r2.url = :url
							AND (:rootOrigin = '' OR r2.origin = :rootOrigin OR r2.origin LIKE CONCAT(:rootOrigin, '.%'))
							AND ((CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r2.metadata, 'expandedTags'), r2.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
							THEN CASE WHEN r2.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END) > (CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
							THEN CASE WHEN r.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END)
								OR ((CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r2.metadata, 'expandedTags'), r2.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
							THEN CASE WHEN r2.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END) = (CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
							THEN CASE WHEN r.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END)
									AND r2.modified > r.modified))
					)
					THEN cast_to_jsonb('true')
					ELSE cast_to_jsonb('false')
				END,
				true
			)
			WHERE r.url = :url
				AND (:rootOrigin = '' OR r.origin = :rootOrigin OR r.origin LIKE CONCAT(:rootOrigin, '.%'))""")
			.setParameter("url", url)
			.setParameter("rootOrigin", rootOrigin)
			.setParameter("pinned", pinned)
			.executeUpdate();
	}

	@Override
	public boolean higherRankExists(String url, String origin, String rootOrigin, boolean pinned, int rank, Instant modified) {
		return em.createQuery("""
			SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Ref r
			WHERE r.url = :url
				AND (:rootOrigin = '' OR r.origin = :rootOrigin OR r.origin LIKE concat(:rootOrigin, '.%'))
				AND ((CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
							THEN CASE WHEN r.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END) > :rank
					OR ((CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
							THEN CASE WHEN r.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END) = :rank
						AND r.modified > :modified))""", Boolean.class)
			.setParameter("url", url)
			.setParameter("rootOrigin", rootOrigin)
			.setParameter("pinned", pinned)
			.setParameter("rank", rank)
			.setParameter("modified", modified)
			.getSingleResult();
	}

	@Override
	@Transactional
	public int pushAsyncMetadata(Ref ref) {
		return em.createQuery("""
			UPDATE Ref SET
				title = :title,
				comment = :comment,
				tags = :tags,
				sources = :sources,
				alternateUrls = :alternateUrls,
				plugins = :plugins,
				metadata = jsonb_concat(jsonb_set(COALESCE(metadata, cast_to_jsonb('{}')), '{ignored}', cast_to_jsonb('false'), true), :partialMetadata),
				published = :published,
				modified = :modified
			WHERE
				url = :url AND
				origin = :origin""")
			.setParameter("url", ref.getUrl())
			.setParameter("origin", ref.getOrigin())
			.setParameter("title", ref.getTitle())
			.setParameter("comment", ref.getComment())
			.setParameter("tags", ref.getTags())
			.setParameter("sources", ref.getSources())
			.setParameter("alternateUrls", ref.getAlternateUrls())
			.setParameter("plugins", ref.getPlugins())
			.setParameter("partialMetadata", ref.getMetadata())
			.setParameter("published", ref.getPublished())
			.setParameter("modified", ref.getModified())
			.executeUpdate();
	}

	/**
	 * Latest wins: metadata is server-generated and recomputed by cascade/regen.
	 * Concurrent delta writes (addResponse/removePlugins on a stale copy) can lose
	 * an update; the next cascade/regen recompute fixes it.
	 */
	@Override
	@Transactional
	public int updateMetadata(String url, String origin, Instant modified, Metadata metadata) {
		return em.createQuery("""
			UPDATE Ref r
			SET r.metadata = :metadata
			WHERE r.url = :url
				AND r.origin = :origin""")
			.setParameter("url", url)
			.setParameter("origin", origin)
			.setParameter("metadata", metadata)
			.executeUpdate();
	}

	@Override
	@Transactional
	public int markCascade(String url, String origin, Instant modified) {
		return em.createQuery("""
			UPDATE Ref r
			SET r.metadata = jsonb_set(COALESCE(r.metadata, cast_to_jsonb('{}')), '{cascade}', cast_to_jsonb('true'), true)
			WHERE r.url = :url
				AND r.origin = :origin""")
			.setParameter("url", url)
			.setParameter("origin", origin)
			.executeUpdate();
	}
}
