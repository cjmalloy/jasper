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

/**
 * Archive mode Ref queries. Multiple versions of a Ref may exist in each origin.
 * Archive mode is only supported on PostgreSQL.
 */
@Repository
@Profile("archive & !sqlite")
public class RefRepositoryImplArchive implements RefRepositoryCustom {

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
				AND NOT EXISTS (SELECT 1 FROM ref n WHERE n.url = r.url AND n.origin = r.origin AND n.modified > r.modified)
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

	/**
	 * Older versions of a user URL are skipped.
	 */
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
			AND NOT EXISTS (SELECT 1 FROM Ref n WHERE n.url = r.url AND n.origin = r.origin AND n.modified > r.modified)
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""", RefId.class)
			.setParameter("url", url)
			.setParameter("origin", origin)
			.setParameter("tag", tag)
			.getResultList();
	}

	/**
	 * Only the latest version in each origin is ranked, older versions are always obsolete.
	 */
	@Override
	@Transactional
	public int updateObsolete(String url, String rootOrigin, boolean pinned) {
		return em.createNativeQuery("""
		WITH latest AS (
			SELECT DISTINCT ON (l.origin) l.origin, l.modified, CASE WHEN :pinned = true AND jsonb_exists(COALESCE(l.metadata->'expandedTags', l.tags, CAST('[]' AS jsonb)), 'plugin/pinned')
				THEN CASE WHEN l.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END AS rank
			FROM ref l
			WHERE l.url = :url
				AND (:rootOrigin = '' OR l.origin = :rootOrigin OR l.origin LIKE concat(:rootOrigin, '.%'))
			ORDER BY l.origin, l.modified DESC)
		UPDATE ref r
		SET metadata = jsonb_set(
			COALESCE(r.metadata, CAST('{}' AS jsonb)),
			'{obsolete}',
			to_jsonb(EXISTS (
				SELECT 1 FROM latest l
				CROSS JOIN LATERAL (SELECT CASE WHEN :pinned = true AND jsonb_exists(COALESCE(r.metadata->'expandedTags', r.tags, CAST('[]' AS jsonb)), 'plugin/pinned')
				THEN CASE WHEN r.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END AS rank) rr
				WHERE (l.origin = r.origin AND l.modified > r.modified)
					OR l.rank > rr.rank
					OR (l.rank = rr.rank AND l.modified > r.modified))),
			true)
		WHERE r.url = :url
			AND (:rootOrigin = '' OR r.origin = :rootOrigin OR r.origin LIKE concat(:rootOrigin, '.%'))
			""")
			.setParameter("url", url)
			.setParameter("rootOrigin", rootOrigin)
			.setParameter("pinned", pinned)
			.executeUpdate();
	}

	/**
	 * Also true if a newer version exists in the same origin.
	 */
	@Override
	public boolean higherRankExists(String url, String origin, String rootOrigin, boolean pinned, int rank, Instant modified) {
		return (Boolean) em.createNativeQuery("""
		SELECT EXISTS (
			SELECT 1 FROM (
				SELECT DISTINCT ON (l.origin) l.origin, l.modified, CASE WHEN :pinned = true AND jsonb_exists(COALESCE(l.metadata->'expandedTags', l.tags, CAST('[]' AS jsonb)), 'plugin/pinned')
				THEN CASE WHEN l.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END AS rank
				FROM ref l
				WHERE l.url = :url
					AND (:rootOrigin = '' OR l.origin = :rootOrigin OR l.origin LIKE concat(:rootOrigin, '.%'))
				ORDER BY l.origin, l.modified DESC) l
			WHERE (l.origin = :origin AND l.modified > :modified)
				OR l.rank > :rank
				OR (l.rank = :rank AND l.modified > :modified))
			""", Boolean.class)
			.setParameter("url", url)
			.setParameter("origin", origin)
			.setParameter("rootOrigin", rootOrigin)
			.setParameter("pinned", pinned)
			.setParameter("rank", rank)
			.setParameter("modified", modified)
			.getSingleResult();
	}

	/**
	 * Only updates the version with the same modified date, so a new version is inserted instead.
	 */
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
				published = :published
			WHERE
				url = :url AND
				origin = :origin AND
				modified = :modified""")
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
	 * Only updates the given version.
	 */
	@Override
	@Transactional
	public int updateMetadata(String url, String origin, Instant modified, Metadata metadata) {
		return em.createQuery("""
			UPDATE Ref r
			SET r.metadata = :metadata
			WHERE r.url = :url
				AND r.origin = :origin
				AND r.modified = :modified""")
			.setParameter("url", url)
			.setParameter("origin", origin)
			.setParameter("modified", modified)
			.setParameter("metadata", metadata)
			.executeUpdate();
	}

	/**
	 * Only marks the given version.
	 */
	@Override
	@Transactional
	public int markCascade(String url, String origin, Instant modified) {
		return em.createQuery("""
			UPDATE Ref r
			SET r.metadata = jsonb_set(COALESCE(r.metadata, cast_to_jsonb('{}')), '{cascade}', cast_to_jsonb('true'), true)
			WHERE r.url = :url
				AND r.origin = :origin
				AND r.modified = :modified""")
			.setParameter("url", url)
			.setParameter("origin", origin)
			.setParameter("modified", modified)
			.executeUpdate();
	}
}
