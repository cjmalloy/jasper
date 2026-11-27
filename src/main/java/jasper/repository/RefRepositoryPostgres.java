package jasper.repository;

import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.domain.RefId;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
@Profile("!sqlite & !archive")
@Transactional(readOnly = true)
public interface RefRepositoryPostgres extends RefRepository {

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
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""")
	List<RefId> findAllResponseIdsWithTag(String url, String origin, String tag);

	@Override
	@Transactional
	@Modifying
	@Query("""
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
	int updateObsolete(String url, String rootOrigin, boolean pinned);

	@Override
	@Query("""
		SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Ref r
		WHERE r.url = :url
			AND (:rootOrigin = '' OR r.origin = :rootOrigin OR r.origin LIKE concat(:rootOrigin, '.%'))
			AND ((CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
						THEN CASE WHEN r.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END) > :rank
				OR ((CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
						THEN CASE WHEN r.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END) = :rank
					AND r.modified > :modified))""")
	boolean higherRankExists(String url, String origin, String rootOrigin, boolean pinned, int rank, Instant modified);

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
			published = :#{#ref.published},
			modified = :#{#ref.modified}
		WHERE
			url = :#{#ref.url} AND
			origin = :#{#ref.origin}""")
	int pushAsyncMetadata(Ref ref);

	/**
	 * Latest wins: metadata is server-generated and recomputed by cascade/regen.
	 * Concurrent delta writes (addResponse/removePlugins on a stale copy) can lose
	 * an update; the next cascade/regen recompute fixes it.
	 */
	@Override
	@Transactional
	@Modifying
	@Query("""
		UPDATE Ref r
		SET r.metadata = :metadata
		WHERE r.url = :url
			AND r.origin = :origin""")
	int updateMetadata(String url, String origin, Instant modified, Metadata metadata);

	@Override
	@Transactional
	@Modifying
	@Query("""
		UPDATE Ref r
		SET r.metadata = jsonb_set(COALESCE(r.metadata, cast_to_jsonb('{}')), '{cascade}', cast_to_jsonb('true'), true)
		WHERE r.url = :url
			AND r.origin = :origin""")
	int markCascade(String url, String origin, Instant modified);
}
