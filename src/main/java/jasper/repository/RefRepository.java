package jasper.repository;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.domain.RefId;
import jasper.domain.proj.RefUrl;
import jasper.domain.proj.RefView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
@Transactional(readOnly = true)
public interface RefRepository extends JpaRepository<Ref, RefId>, JpaSpecificationExecutor<Ref>, StreamMixin<RefView>, ModifiedCursor, OriginMixin {

	Optional<Ref> findFirstByUrlAndOriginOrderByModifiedDesc(String url, String origin);

	/**
	 * Find the latest version. In archive mode multiple versions may exist.
	 */
	default Optional<Ref> findOneByUrlAndOrigin(String url, String origin) {
		return findFirstByUrlAndOriginOrderByModifiedDesc(url, origin);
	}

	void deleteByUrlAndOrigin(String url, String origin);
	boolean existsByUrlAndOrigin(String url, String origin);

	@Modifying
	@Query("""
		UPDATE Ref SET
			title = :title,
			comment = :comment,
			tags = :tags,
			sources = :sources,
			alternateUrls = :alternateUrls,
			plugins = :plugins,
			metadata = :metadata,
			published = :published,
			modified = :modified
		WHERE
			url = :url AND
			origin = :origin AND
			modified = :cursor""")
	int optimisticUpdate(
		Instant cursor,
		String url,
		String origin,
		String title,
		String comment,
		List<String> tags,
		List<String> sources,
		List<String> alternateUrls,
		ObjectNode plugins,
		Metadata metadata,
		Instant published,
		Instant modified);

	@Transactional
	@Modifying
	@Query("""
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
	int pushAsyncMetadata(
		String url,
		String origin,
		String title,
		String comment,
		List<String> tags,
		List<String> sources,
		List<String> alternateUrls,
		ObjectNode plugins,
		Metadata partialMetadata,
		Instant published,
		Instant modified);

	/**
	 * Archive mode version of {@link #pushAsyncMetadata}: only updates the
	 * version with the same modified date so a new version is inserted instead.
	 */
	@Transactional
	@Modifying
	@Query("""
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
	int pushAsyncMetadataVersion(
		String url,
		String origin,
		String title,
		String comment,
		List<String> tags,
		List<String> sources,
		List<String> alternateUrls,
		ObjectNode plugins,
		Metadata partialMetadata,
		Instant published,
		Instant modified);

	@Query("""
		SELECT max(r.modified)
		FROM Ref r
		WHERE r.origin = :origin
			AND COALESCE(jsonb_object_field_text(r.metadata, 'ignored'), 'false') != 'true'""")
	Instant getCursor(String origin);

	@Transactional
	@Modifying
	@Query("""
		UPDATE Ref SET
			metadata = jsonb_set(metadata, '{ignored}', cast_to_jsonb('false'), true)
		WHERE origin = :origin
			AND modified <= :upTo
			AND jsonb_object_field_text(metadata, 'ignored') = 'true'""")
	int clearIgnored(String origin, Instant upTo);

	@Query(nativeQuery = true, value = "SELECT DISTINCT origin from ref")
	List<String> origins();

	@Modifying(clearAutomatically = true)
	@Query("""
		DELETE FROM Ref ref
		WHERE ref.origin = :origin
			AND ref.modified <= :olderThan""")
	void deleteByOriginAndModifiedLessThanEqual(String origin, Instant olderThan);

	/**
	 * Remove every version modified at or before olderThan. Only used to prune in archive mode.
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		DELETE FROM Ref ref
		WHERE ref.url = :url
			AND ref.origin = :origin
			AND ref.modified <= :olderThan""")
	void deleteByUrlAndOriginAndModifiedLessThanEqual(String url, String origin, Instant olderThan);

	@Query("""
		FROM Ref ref
		WHERE ref.url = :url
			AND ref.published >= :published
			AND (:includeInternal = true OR jsonb_exists(COALESCE(jsonb_object_field(ref.metadata, 'expandedTags'), ref.tags, cast_to_jsonb('[]')), 'internal') = false)
			AND COALESCE(jsonb_object_field_text(ref.metadata, 'obsolete'), 'false') != 'true'
			AND (:origin = '' OR ref.origin = :origin OR ref.origin LIKE concat(:origin, '.%'))""")
	List<Ref> findAllPublishedByUrlAndPublishedGreaterThanEqual(String url, String origin, Instant published, boolean includeInternal);

	@Query("""
		FROM Ref r
		WHERE r.url != :url
			AND r.published <= :published
			AND jsonb_exists(r.sources, :url) = true
			AND (:includeInternal = true OR jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags, cast_to_jsonb('[]')), 'internal') = false)
			AND COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true'
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""")
	List<Ref> findAllResponsesPublishedBeforeThanEqual(String url, String origin, Instant published, boolean includeInternal);

	@Query("""
		SELECT r.url FROM Ref r
		WHERE r.url != :url
			AND jsonb_exists(r.sources, :url) = true
			AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags), :tag) = true
			AND COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true'
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""")
	List<String> findAllResponsesWithTag(String url, String origin, String tag);

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

	/**
	 * Archive mode version of {@link #findAllResponseIdsWithTag}: older versions of a user URL are skipped.
	 */
	@Query("""
		SELECT new jasper.domain.RefId(r.url, r.origin) FROM Ref r
		WHERE r.url != :url
			AND jsonb_exists(r.sources, :url) = true
			AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags), :tag) = true
			AND ((r.url IN ('tag:/user', 'tag:/+user', 'tag:/_user')
					OR r.url LIKE 'tag:/user/%' OR r.url LIKE 'tag:/user?%'
					OR r.url LIKE 'tag:/+user/%' OR r.url LIKE 'tag:/+user?%'
					OR r.url LIKE 'tag:/\\_user/%' ESCAPE '\\' OR r.url LIKE 'tag:/\\_user?%' ESCAPE '\\')
				AND NOT EXISTS (SELECT 1 FROM Ref n WHERE n.url = r.url AND n.origin = r.origin AND n.modified > r.modified)
				OR COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true')
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""")
	List<RefId> findAllLatestResponseIdsWithTag(String url, String origin, String tag);

	default List<RefId> findAllResponseIdsWithTag(String url, String origin, String tag, boolean archive) {
		return archive
			? findAllLatestResponseIdsWithTag(url, origin, tag)
			: findAllResponseIdsWithTag(url, origin, tag);
	}

	@Query("""
		SELECT r.url FROM Ref r
		WHERE r.url != :url
			AND jsonb_exists(r.sources, :url) = true
			AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags), :tag) = false
			AND COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true'
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""")
	List<String> findAllResponsesWithoutTag(String url, String origin, String tag);

	/**
	 * Latest created date of a response, ignoring user urls.
	 */
	@Query("""
		SELECT MAX(r.created) FROM Ref r
		WHERE r.url != :url
			AND jsonb_exists(r.sources, :url) = true
			AND r.url NOT IN ('tag:/user', 'tag:/+user', 'tag:/_user')
			AND r.url NOT LIKE 'tag:/user/%' AND r.url NOT LIKE 'tag:/user?%'
			AND r.url NOT LIKE 'tag:/+user/%' AND r.url NOT LIKE 'tag:/+user?%'
			AND r.url NOT LIKE 'tag:/\\_user/%' ESCAPE '\\' AND r.url NOT LIKE 'tag:/\\_user?%' ESCAPE '\\'
			AND r.metadata IS NOT NULL
			AND COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true'
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""")
	Instant latestResponseCreated(String url, String origin);

	/**
	 * Latest modified date of a response, including user urls.
	 */
	@Query("""
		SELECT MAX(r.modified) FROM Ref r
		WHERE r.url != :url
			AND jsonb_exists(r.sources, :url) = true
			AND (r.url IN ('tag:/user', 'tag:/+user', 'tag:/_user')
				OR r.url LIKE 'tag:/user/%' OR r.url LIKE 'tag:/user?%'
				OR r.url LIKE 'tag:/+user/%' OR r.url LIKE 'tag:/+user?%'
				OR r.url LIKE 'tag:/\\_user/%' ESCAPE '\\' OR r.url LIKE 'tag:/\\_user?%' ESCAPE '\\'
				OR (r.metadata IS NOT NULL AND COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true'))
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""")
	Instant latestResponseModified(String url, String origin);

	@Transactional
	@Query("""
		SELECT r FROM Ref r
		WHERE r.url != :url
			AND NOT EXISTS (
				SELECT 1 FROM Ref s
				WHERE s.url = :url
					AND jsonb_exists(s.sources, r.url) = true
					AND COALESCE(jsonb_object_field_text(s.metadata, 'obsolete'), 'false') != 'true'
					AND (:origin = '' OR s.origin = :origin OR s.origin LIKE concat(:origin, '.%')))
			AND (jsonb_exists(jsonb_object_field(r.metadata, 'responses'), :url) = true
					OR jsonb_exists(jsonb_object_field(r.metadata, 'internalResponses'), :url) = true)
			AND COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true'
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""")
	Stream<Ref> findRemovedSources(String url, String origin);

	@Query("""
		SELECT r.url FROM Ref r
		WHERE r.url != :url
			AND NOT EXISTS (
				SELECT 1 FROM Ref s
				WHERE s.url = :url
					AND jsonb_exists(s.sources, r.url) = true
					AND COALESCE(jsonb_object_field_text(s.metadata, 'obsolete'), 'false') != 'true'
					AND (:origin = '' OR s.origin = :origin OR s.origin LIKE concat(:origin, '.%')))
			AND (jsonb_exists(jsonb_object_field(r.metadata, 'responses'), :url) = true
					OR jsonb_exists(jsonb_object_field(r.metadata, 'internalResponses'), :url) = true)
			AND COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true'
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""")
	List<String> findRemovedSourceUrls(String url, String origin);

	/**
	 * Only the highest ranked version of a Ref is not obsolete.
	 * Versions in the root origin tagged plugin/pinned rank highest, then versions in other origins
	 * tagged plugin/pinned, then everything else. The newest version wins within a rank.
	 * Refs are only ranked by plugin/pinned if the plugin is installed in the root origin.
	 * In archive mode only the latest version in each origin is ranked, older versions are always obsolete.
	 */
	@Modifying
	@Transactional
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
						AND ((r2.origin = r.origin AND r2.modified > r.modified)
							OR (NOT EXISTS (SELECT 1 FROM Ref r3 WHERE r3.url = r2.url AND r3.origin = r2.origin AND r3.modified > r2.modified)
								AND ((CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r2.metadata, 'expandedTags'), r2.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
						THEN CASE WHEN r2.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END) > (CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
						THEN CASE WHEN r.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END)
								OR ((CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r2.metadata, 'expandedTags'), r2.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
						THEN CASE WHEN r2.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END) = (CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
						THEN CASE WHEN r.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END)
									AND r2.modified > r.modified))))
				)
				THEN cast_to_jsonb('true')
				ELSE cast_to_jsonb('false')
			END,
			true
		)
		WHERE r.url = :url
			AND (:rootOrigin = '' OR r.origin = :rootOrigin OR r.origin LIKE CONCAT(:rootOrigin, '.%'))
		""")
	int updateObsolete(String url, String rootOrigin, boolean pinned);

	/**
	 * Check if a higher ranked version of a Ref exists, or a newer version in the same origin.
	 * @see #updateObsolete(String, String, boolean)
	 */
	@Query("""
		SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Ref r
		WHERE r.url = :url
			AND (:rootOrigin = '' OR r.origin = :rootOrigin OR r.origin LIKE concat(:rootOrigin, '.%'))
			AND ((r.origin = :origin AND r.modified > :modified)
				OR (NOT EXISTS (SELECT 1 FROM Ref n WHERE n.url = r.url AND n.origin = r.origin AND n.modified > r.modified)
				AND ((CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
						THEN CASE WHEN r.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END) > :rank
				OR ((CASE WHEN :pinned = true AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags, cast_to_jsonb('[]')), 'plugin/pinned') = true
						THEN CASE WHEN r.origin = :rootOrigin THEN 2 ELSE 1 END ELSE 0 END) = :rank
					AND r.modified > :modified))))""")
	boolean higherRankExists(String url, String origin, String rootOrigin, boolean pinned, int rank, Instant modified);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query("""
		UPDATE Ref r
		SET r.metadata = jsonb_set(r.metadata, '{regen}', cast_to_jsonb('true'), true)
		WHERE r.metadata IS NOT NULL
			AND COALESCE(jsonb_object_field_text(r.metadata, 'regen'), 'false') != 'true'
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))""")
	void dropMetadata(String origin);

	/**
	 * Mark every version of a Ref for regen if any version has the given tag.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Transactional
	@Query("""
		UPDATE Ref r
		SET r.metadata = jsonb_set(r.metadata, '{regen}', cast_to_jsonb('true'), true)
		WHERE r.metadata IS NOT NULL
			AND COALESCE(jsonb_object_field_text(r.metadata, 'regen'), 'false') != 'true'
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))
			AND EXISTS (
				SELECT 1 FROM Ref t
				WHERE t.url = r.url
					AND jsonb_exists(COALESCE(jsonb_object_field(t.metadata, 'expandedTags'), t.tags, cast_to_jsonb('[]')), :tag) = true
					AND (:origin = '' OR t.origin = :origin OR t.origin LIKE concat(:origin, '.%')))""")
	int regenTag(String origin, String tag);

	// Latest wins: metadata is server-generated and recomputed by cascade/regen.
	// Concurrent delta writes (addResponse/removePlugins on a stale copy) can lose
	// an update; the next cascade/regen recompute fixes it.
	@Modifying
	@Transactional
	@Query("""
		UPDATE Ref r
		SET r.metadata = :metadata
		WHERE r.url = :url
			AND r.origin = :origin""")
	int updateMetadata(String url, String origin, Metadata metadata);

	/**
	 * Archive mode version of {@link #updateMetadata}: only updates a single version.
	 */
	@Modifying
	@Transactional
	@Query("""
		UPDATE Ref r
		SET r.metadata = :metadata
		WHERE r.url = :url
			AND r.origin = :origin
			AND r.modified = :modified""")
	int updateMetadataVersion(String url, String origin, Instant modified, Metadata metadata);

	@Modifying
	@Transactional
	@Query("""
		UPDATE Ref r
		SET r.metadata = jsonb_set(COALESCE(r.metadata, cast_to_jsonb('{}')), '{cascade}', cast_to_jsonb('true'), true)
		WHERE r.url = :url
			AND r.origin = :origin""")
	int markCascade(String url, String origin);

	/**
	 * Archive mode version of {@link #markCascade}: only updates a single version.
	 */
	@Modifying
	@Transactional
	@Query("""
		UPDATE Ref r
		SET r.metadata = jsonb_set(COALESCE(r.metadata, cast_to_jsonb('{}')), '{cascade}', cast_to_jsonb('true'), true)
		WHERE r.url = :url
			AND r.origin = :origin
			AND r.modified = :modified""")
	int markCascadeVersion(String url, String origin, Instant modified);

	@Modifying
	@Transactional
	@Query("""
		UPDATE Ref r
		SET r.metadata = jsonb_set(r.metadata, '{cascade}', cast_to_jsonb('false'), true)
		WHERE r.url = :url
			AND r.origin = :origin
			AND r.modified = :modified
			AND r.metadata IS NOT NULL""")
	int clearCascade(String url, String origin, Instant modified);

	@Query("""
		FROM Ref r
		WHERE jsonb_object_field_text(r.metadata, 'cascade') = 'true'
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))
		ORDER BY r.modified DESC
		FETCH FIRST 1 ROW ONLY""")
	Optional<Ref> getRefCascade(String origin);

	@Query("""
		FROM Ref r
		WHERE (r.metadata IS NULL OR jsonb_object_field_text(r.metadata, 'modified') IS NULL OR jsonb_object_field_text(r.metadata, 'regen') = 'true')
			AND (:origin = '' OR r.origin = :origin OR r.origin LIKE concat(:origin, '.%'))
		ORDER BY r.modified DESC
		FETCH FIRST 1 ROW ONLY""")
	Optional<Ref> getRefBackfill(String origin);

	@Query("""
		SELECT r.url AS url, jsonb_object_field_text(jsonb_object_field(r.plugins, '+plugin/origin'), 'proxy') AS proxy
		FROM Ref r
		WHERE r.origin = :origin
			AND jsonb_exists(COALESCE(jsonb_object_field(r.metadata, 'expandedTags'), r.tags), '+plugin/origin') = true
			AND ((jsonb_object_field_text(jsonb_object_field(r.plugins, '+plugin/origin'), 'local') IS NULL AND '' = :remote)
				OR jsonb_object_field_text(jsonb_object_field(r.plugins, '+plugin/origin'), 'local') = :remote)
		FETCH FIRST 1 ROW ONLY""")
	Optional<RefUrl> originUrl(String origin, String remote);

	@Query("""
		SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM Ref r
		WHERE jsonb_object_field_text(jsonb_object_field(r.plugins, '_plugin/cache'), 'id') = :id
			AND COALESCE(jsonb_object_field_text(jsonb_object_field(r.plugins, '_plugin/cache'), 'ban'), '') != 'true'
			AND COALESCE(jsonb_object_field_text(jsonb_object_field(r.plugins, '_plugin/cache'), 'noStore'), '') != 'true'""")
	boolean cacheExists(String id);

}
