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
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Abstract methods are profile-specific queries, declared with @Query in
 * {@link RefRepositoryPostgres}, {@link RefRepositorySqlite} and {@link RefRepositoryArchive}.
 */
@NoRepositoryBean
@Transactional(readOnly = true)
public interface RefRepository extends JpaRepository<Ref, RefId>, JpaSpecificationExecutor<Ref>, StreamMixin<RefView>, ModifiedCursor, OriginMixin {

	List<String> findAllPluginTagsInResponses(String url, String origin);
	List<Object[]> countPluginTagsInResponses(String url, String origin);
	List<Object[]> countLocalPluginTagsInResponses(String url, String origin);
	List<String> findAllUserPluginTagsInResponses(String url, String origin);
	List<RefId> findAllResponseIdsWithTag(String url, String origin, String tag);
	/**
	 * Only the highest ranked version of a Ref is not obsolete.
	 * Versions in the root origin tagged plugin/pinned rank highest, then versions in other origins
	 * tagged plugin/pinned, then everything else. The newest version wins within a rank.
	 * Refs are only ranked by plugin/pinned if the plugin is installed in the root origin.
	 */
	int updateObsolete(String url, String rootOrigin, boolean pinned);
	/**
	 * Check if a higher ranked version of a Ref exists.
	 * @see #updateObsolete(String, String, boolean)
	 */
	boolean higherRankExists(String url, String origin, String rootOrigin, boolean pinned, int rank, Instant modified);
	/**
	 * Update an existing Ref, merging the partial metadata.
	 * @return number of rows updated, 0 if the Ref should be inserted instead
	 */
	int pushAsyncMetadata(Ref ref);
	/**
	 * Write only the metadata of a Ref.
	 */
	int updateMetadata(String url, String origin, Instant modified, Metadata metadata);
	/**
	 * Queue a Ref for the cascade.
	 */
	int markCascade(String url, String origin, Instant modified);

	Optional<Ref> findFirstByUrlAndOriginOrderByModifiedDesc(String url, String origin);

	/**
	 * Find the latest version. In archive mode multiple versions may exist.
	 */
	default Optional<Ref> findOneByUrlAndOrigin(String url, String origin) {
		return findFirstByUrlAndOriginOrderByModifiedDesc(url, origin);
	}

	/**
	 * The current version, or empty if it is missing.
	 * Overridden in {@link RefRepositoryArchive} to treat a tombstone as missing.
	 */
	default Optional<Ref> findCurrent(String url, String origin) {
		return findOneByUrlAndOrigin(url, origin);
	}

	void deleteByUrlAndOrigin(String url, String origin);
	boolean existsByUrlAndOrigin(String url, String origin);

	boolean existsByUrlAndOriginAndModifiedGreaterThan(String url, String origin, Instant modified);

	/**
	 * Archive mode: mark versions in the origin older than the given version as obsolete.
	 */
	@Modifying(flushAutomatically = true)
	@Transactional
	@Query("""
		UPDATE Ref r
		SET r.metadata = jsonb_set(coalesce(r.metadata, cast_to_jsonb('{"regen":true}')), '{obsolete}', cast_to_jsonb('true'), true)
		WHERE r.url = :url
			AND r.origin = :origin
			AND r.modified < :modified
			AND COALESCE(jsonb_object_field_text(r.metadata, 'obsolete'), 'false') != 'true'""")
	int setOlderVersionsObsolete(String url, String origin, Instant modified);

	/**
	 * Archive mode: current versions in the origin with the given alternate URL.
	 */
	@Query("""
		FROM Ref r
		WHERE r.origin = :origin
			AND jsonb_exists(r.alternateUrls, :url) = true
			AND NOT EXISTS (SELECT 1 FROM Ref n WHERE n.url = r.url AND n.origin = r.origin AND n.modified > r.modified)""")
	List<Ref> findAllCurrentByAlternateUrlAndOrigin(String url, String origin);

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
