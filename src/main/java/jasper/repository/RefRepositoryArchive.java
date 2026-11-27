package jasper.repository;

import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.domain.RefId;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static jasper.repository.spec.RefSpec.isLatest;
import static jasper.util.Archive.isBlank;

/**
 * Archive mode Ref queries. Multiple versions of a Ref may exist in each origin.
 * Archive mode is only supported on PostgreSQL.
 */
@Repository
@Profile("archive & !sqlite")
@Transactional(readOnly = true)
public interface RefRepositoryArchive extends RefRepository {

	@Override
	@Query(nativeQuery = true, value = """
		SELECT DISTINCT t.tag
		FROM ref r
			CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(r.metadata->'expandedTags', r.tags)) AS t(tag)
		WHERE r.url != :url
			AND r.sources @> jsonb_build_array(:url)
			AND t.tag ~ '^[_+]?plugin(/|$)'
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""")
	List<String> findAllPluginTagsInResponses(String url, String origin);

	@Override
	@Query(nativeQuery = true, value = """
		SELECT t.tag, COUNT(DISTINCT (r.url, r.origin))
		FROM ref r
			CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(r.metadata->'expandedTags', r.tags)) AS t(tag)
		WHERE r.url != :url
			AND r.sources @> jsonb_build_array(:url)
			AND t.tag ~ '^[_+]?plugin(/|$)'
			AND (r.url ~ '^tag:/[_+]?user([/?]|$)'
				OR COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true')
			AND NOT EXISTS (SELECT 1 FROM ref n WHERE n.url = r.url AND n.origin = r.origin AND n.modified > r.modified)
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))
		GROUP BY t.tag""")
	List<Object[]> countPluginTagsInResponses(String url, String origin);

	@Override
	@Query(nativeQuery = true, value = """
		SELECT t.tag, COUNT(DISTINCT r.url)
		FROM ref r
			CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(r.metadata->'expandedTags', r.tags)) AS t(tag)
		WHERE r.url != :url
			AND r.sources @> jsonb_build_array(:url)
			AND t.tag ~ '^[_+]?plugin(/|$)'
			AND (r.url ~ '^tag:/[_+]?user([/?]|$)'
				OR COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true')
			AND NOT EXISTS (SELECT 1 FROM ref n WHERE n.url = r.url AND n.origin = r.origin AND n.modified > r.modified)
			AND r.origin = :origin
		GROUP BY t.tag""")
	List<Object[]> countLocalPluginTagsInResponses(String url, String origin);

	@Override
	@Query(nativeQuery = true, value = """
		SELECT DISTINCT t.tag
		FROM ref r
			CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(r.metadata->'expandedTags', r.tags)) AS t(tag)
		WHERE r.url != :url
			AND r.sources @> jsonb_build_array(:url)
			AND t.tag ~ '^[_+]?plugin/user(/|$)'
			AND r.origin = :origin""")
	List<String> findAllUserPluginTagsInResponses(String url, String origin);

	/**
	 * Older versions of a user URL are skipped.
	 */
	@Override
	@Query("""
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
		AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""")
	List<RefId> findAllResponseIdsWithTag(String url, String origin, String tag);

	/**
	 * Only the latest version in each origin is ranked, older versions are always obsolete.
	 * Rows whose obsolete flag is already correct are not rewritten.
	 */
	@Override
	@Transactional
	@Modifying
	@Query(nativeQuery = true, value = """
	WITH latest AS (
		SELECT DISTINCT ON (l.origin) l.origin, l.modified, CASE WHEN :pinned = true AND jsonb_exists(COALESCE(l.metadata->'expandedTags', l.tags, CAST('[]' AS jsonb)), 'plugin/pinned')
			THEN CASE WHEN l.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END AS rank
		FROM ref l
		WHERE l.url = :url
			AND (:rootOrigin = '' OR l.origin = :rootOrigin OR l.origin LIKE concat(:rootOrigin, '.%'))
		ORDER BY l.origin, l.modified DESC),
	ranked AS (
		SELECT c.origin, c.modified, EXISTS (
			SELECT 1 FROM latest l
			CROSS JOIN LATERAL (SELECT CASE WHEN :pinned = true AND jsonb_exists(COALESCE(c.metadata->'expandedTags', c.tags, CAST('[]' AS jsonb)), 'plugin/pinned')
			THEN CASE WHEN c.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END AS rank) rr
			WHERE (l.origin = c.origin AND l.modified > c.modified)
				OR l.rank > rr.rank
				OR (l.rank = rr.rank AND l.modified > c.modified)) AS obsolete
		FROM ref c
		WHERE c.url = :url
			AND (:rootOrigin = '' OR c.origin = :rootOrigin OR c.origin LIKE concat(:rootOrigin, '.%')))
	UPDATE ref r
	SET metadata = jsonb_set(COALESCE(r.metadata, CAST('{}' AS jsonb)), '{obsolete}', to_jsonb(k.obsolete), true)
	FROM ranked k
	WHERE r.url = :url
		AND r.origin = k.origin
		AND r.modified = k.modified
		AND (r.metadata IS NULL OR (COALESCE(r.metadata->>'obsolete', 'false') NOT IN ('false', '0')) != k.obsolete)""")
	int updateObsolete(String url, String rootOrigin, boolean pinned);

	/**
	 * Also true if a newer version exists in the same origin.
	 */
	@Override
	@Query(nativeQuery = true, value = """
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
			OR (l.rank = :rank AND l.modified > :modified))""")
	boolean higherRankExists(String url, String origin, String rootOrigin, boolean pinned, int rank, Instant modified);

	/**
	 * Only updates the version with the same modified date, so a new version is inserted instead.
	 */
	@Override
	@Transactional
	@Modifying
	@Query("""
		UPDATE Ref SET
			title = :#{#ref.title},
			comment = :#{#ref.comment},
			tags = :#{#ref.tags},
			sources = :#{#ref.sources},
			alternateUrls = :#{#ref.alternateUrls},
			plugins = :#{#ref.plugins},
			metadata = jsonb_concat(jsonb_set(COALESCE(metadata, cast_to_jsonb('{}')), '{ignored}', cast_to_jsonb('false'), true), :#{#ref.metadata}),
			published = :#{#ref.published}
		WHERE
			url = :#{#ref.url} AND
			origin = :#{#ref.origin} AND
			modified = :#{#ref.modified}""")
	int pushAsyncMetadata(Ref ref);

	/**
	 * Only updates the given version.
	 */
	@Override
	@Transactional
	@Modifying
	@Query("""
		UPDATE Ref r
		SET r.metadata = :metadata
		WHERE r.url = :url
			AND r.origin = :origin
			AND r.modified = :modified""")
	int updateMetadata(String url, String origin, Instant modified, Metadata metadata);

	/**
	 * Only marks the given version.
	 */
	@Override
	@Transactional
	@Modifying
	@Query("""
		UPDATE Ref r
		SET r.metadata = jsonb_set(COALESCE(r.metadata, cast_to_jsonb('{}')), '{cascade}', cast_to_jsonb('true'), true)
		WHERE r.url = :url
			AND r.origin = :origin
			AND r.modified = :modified""")
	int markCascade(String url, String origin, Instant modified);

	/**
	 * A blank current version is a tombstone and is treated as missing.
	 */
	@Override
	default Optional<Ref> findCurrent(String url, String origin) {
		return findOneByUrlAndOrigin(url, origin).filter(r -> !isBlank(r));
	}

	@Query("""
		FROM Ref r
		WHERE r.origin = :origin
			AND jsonb_exists(r.alternateUrls, :url) = true
			AND NOT EXISTS (SELECT 1 FROM Ref n WHERE n.url = r.url AND n.origin = r.origin AND n.modified > r.modified)""")
	List<Ref> findAllCurrentByAlternateUrlAndOrigin(String url, String origin);

	/**
	 * Only the latest version of each Ref is matched, so older versions don't keep a removed alternate URL.
	 * Tombstones are treated as missing.
	 */
	@Override
	default Optional<Ref> findCurrentByAlternateUrl(String url, String origin) {
		var matches = findAllCurrentByAlternateUrlAndOrigin(url, origin)
			.stream()
			.filter(r -> !isBlank(r))
			.toList();
		if (matches.size() > 1) throw new IncorrectResultSizeDataAccessException(1, matches.size());
		return matches.stream().findFirst();
	}

	@Override
	default List<Ref> findAllLatest(Specification<Ref> spec) {
		return findAll(spec.and(isLatest()));
	}

	@Override
	default Page<Ref> findAllLatest(Specification<Ref> spec, Pageable pageable) {
		return findAll(spec.and(isLatest()), pageable);
	}

	@Override
	default List<Ref> findAllCurrent(Specification<Ref> spec) {
		return findAllLatest(spec).stream()
			.filter(r -> !isBlank(r))
			.toList();
	}
}
