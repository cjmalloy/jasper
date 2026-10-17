package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static jasper.util.Archive.isBlank;

@Repository
@Profile("!sqlite")
@Transactional
public class BackfillRepositoryImplPostgres implements BackfillRepository {

	/**
	 * Responses are filtered by their obsolete flag. Responses with missing
	 * metadata are assumed to be obsolete, except user URLs which are never obsolete.
	 * Only the user URLs of the given origin are rebuilt, user URLs from other origins are kept.
	 * Blank newResponse and newReaction are filled from the latest response, or the Ref created date.
	 * Expanded tags are regenerated from the tags so tag queries match parent tags.
	 * Only the highest ranked version of a Ref is not obsolete: if plugin/pinned is installed, pinned
	 * versions in the given origin rank highest, then pinned versions in other origins. The newest wins within a rank.
	 * In archive mode only the latest version in each origin is ranked, older versions are always obsolete.
	 * Older versions of a user URL in archive mode are skipped.
	 * Rows are matched by modified so only a single version is updated in archive mode.
	 */
	private static final String BACKFILL_METADATA = """
		WITH rows as (
			SELECT url, origin, modified from ref
			WHERE (metadata IS NULL OR metadata->>'regen' = 'true')
			AND (:origin = '' OR origin = :origin OR origin LIKE concat(:origin, '.%'))
			LIMIT :batchSize
		)
		UPDATE ref r
		SET metadata = jsonb_strip_nulls(jsonb_build_object(
			'modified', COALESCE(r.metadata->>'modified', to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')),
			'expandedTags', (SELECT jsonb_agg(DISTINCT x.tag) FROM jsonb_array_elements_text(COALESCE(r.tags, '[]')) AS t(tag)
				CROSS JOIN LATERAL (SELECT t.tag UNION SELECT left(t.tag, g.n - 1) FROM generate_series(1, length(t.tag)) AS g(n) WHERE substr(t.tag, g.n, 1) = '/') AS x(tag)),
			'newResponse', COALESCE(NULLIF(r.metadata->>'newResponse', ''), to_char(GREATEST(r.created, (SELECT MAX(nre.created) FROM ref nre WHERE (nre.sources @> jsonb_build_array(r.url)) AND nre.url != r.url AND (:origin = '' OR nre.origin = :origin OR nre.origin LIKE concat(:origin, '.%')) AND nre.url !~ '^tag:/[_+]?user([/?]|$)' AND nre.metadata IS NOT NULL AND COALESCE(nre.metadata->>'obsolete', 'false') IN ('false', '0'))), 'YYYY-MM-DD"T"HH24:MI:SS.US"000Z"')),
			'newReaction', COALESCE(NULLIF(r.metadata->>'newReaction', ''), to_char(GREATEST(r.created, (SELECT MAX(nra.modified) FROM ref nra WHERE (nra.sources @> jsonb_build_array(r.url)) AND nra.url != r.url AND (:origin = '' OR nra.origin = :origin OR nra.origin LIKE concat(:origin, '.%')) AND ((nra.url ~ '^tag:/[_+]?user([/?]|$)' AND NOT EXISTS (SELECT 1 FROM ref nran WHERE nran.url = nra.url AND nran.origin = nra.origin AND nran.modified > nra.modified)) OR (nra.metadata IS NOT NULL AND COALESCE(nra.metadata->>'obsolete', 'false') IN ('false', '0'))))), 'YYYY-MM-DD"T"HH24:MI:SS.US"000Z"')),
			'responses', (SELECT jsonb_agg(re.url) FROM ref re WHERE (re.sources @> jsonb_build_array(r.url)) AND (:origin = '' OR re.origin = :origin OR re.origin LIKE concat(:origin, '.%')) AND re.metadata IS NOT NULL AND COALESCE(re.metadata->>'obsolete', 'false') IN ('false', '0') AND jsonb_exists(COALESCE(re.metadata->'expandedTags', re.tags), 'internal') = false),
			'internalResponses', (SELECT jsonb_agg(ire.url) FROM ref ire WHERE (ire.sources @> jsonb_build_array(r.url)) AND (:origin = '' OR ire.origin = :origin OR ire.origin LIKE concat(:origin, '.%')) AND ire.metadata IS NOT NULL AND COALESCE(ire.metadata->>'obsolete', 'false') IN ('false', '0') AND jsonb_exists(COALESCE(ire.metadata->'expandedTags', ire.tags), 'internal') = true),
			'plugins', COALESCE((SELECT jsonb_object_agg(lp.tag, lp.cnt) FROM (
				SELECT t.tag, COUNT(DISTINCT lpre.url) AS cnt FROM ref lpre
					CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(lpre.metadata->'expandedTags', lpre.tags)) AS t(tag)
				WHERE (lpre.sources @> jsonb_build_array(r.url)) AND lpre.url != r.url AND lpre.origin = r.origin AND ((lpre.url ~ '^tag:/[_+]?user([/?]|$)' AND NOT EXISTS (SELECT 1 FROM ref lpren WHERE lpren.url = lpre.url AND lpren.origin = lpre.origin AND lpren.modified > lpre.modified)) OR (lpre.metadata IS NOT NULL AND COALESCE(lpre.metadata->>'obsolete', 'false') IN ('false', '0'))) AND t.tag ~ '^[_+]?plugin(/|$)'
				GROUP BY t.tag
			) lp), CAST('{}' AS jsonb)),
			'remotePlugins', COALESCE((SELECT jsonb_object_agg(rp.tag, rp.cnt) FROM (
				SELECT t.tag, COUNT(DISTINCT (pre.url, pre.origin)) AS cnt FROM ref pre
					CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(pre.metadata->'expandedTags', pre.tags)) AS t(tag)
				WHERE (pre.sources @> jsonb_build_array(r.url)) AND pre.url != r.url AND (:origin = '' OR pre.origin = :origin OR pre.origin LIKE concat(:origin, '.%')) AND ((pre.url ~ '^tag:/[_+]?user([/?]|$)' AND NOT EXISTS (SELECT 1 FROM ref pren WHERE pren.url = pre.url AND pren.origin = pre.origin AND pren.modified > pre.modified)) OR (pre.metadata IS NOT NULL AND COALESCE(pre.metadata->>'obsolete', 'false') IN ('false', '0'))) AND t.tag ~ '^[_+]?plugin(/|$)'
				GROUP BY t.tag
			) rp), CAST('{}' AS jsonb)),
			'userUrls', COALESCE((SELECT jsonb_object_agg(uu.tag, uu.urls) FROM (
				SELECT u.tag, jsonb_agg(DISTINCT u.url) AS urls FROM (
					SELECT t.tag, CASE
						WHEN ure.origin = '' THEN pu.url
						WHEN strpos(pu.url, '?') = 0 THEN concat(pu.url, ure.origin)
						ELSE overlay(pu.url PLACING concat(ure.origin, '?') FROM strpos(pu.url, '?') FOR 1)
					END AS url FROM ref ure
						CROSS JOIN LATERAL (SELECT regexp_replace(ure.url, '^tag:/[_+](user([/?]|$))', 'tag:/\\1') AS url) AS pu
						CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(ure.metadata->'expandedTags', ure.tags)) AS t(tag)
					WHERE (ure.sources @> jsonb_build_array(r.url)) AND ure.url != r.url AND ure.origin = :origin AND ((ure.url ~ '^tag:/[_+]?user([/?]|$)' AND NOT EXISTS (SELECT 1 FROM ref uren WHERE uren.url = ure.url AND uren.origin = ure.origin AND uren.modified > ure.modified)) OR (ure.metadata IS NOT NULL AND COALESCE(ure.metadata->>'obsolete', 'false') IN ('false', '0'))) AND t.tag ~ '^[_+]?plugin/user(/|$)'
					UNION ALL
					SELECT kt.tag, ku.url FROM jsonb_each(CASE WHEN jsonb_typeof(r.metadata->'userUrls') = 'object' THEN r.metadata->'userUrls' END) AS kt(tag, urls)
						CROSS JOIN LATERAL jsonb_array_elements_text(CASE WHEN jsonb_typeof(kt.urls) = 'array' THEN kt.urls ELSE CAST('[]' AS jsonb) END) AS ku(url)
					WHERE ku.url IS NOT NULL AND COALESCE(substring(split_part(ku.url, '?', 1) FROM '@.*$'), '') != :origin
				) u
				GROUP BY u.tag
			) uu), CAST('{}' AS jsonb)),
			'obsolete', EXISTS (SELECT 1 FROM ref n
				CROSS JOIN LATERAL (SELECT (CASE WHEN :pinned = true
					AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(COALESCE(n.tags, '[]')) AS t(tag) WHERE t.tag = 'plugin/pinned' OR t.tag LIKE 'plugin/pinned/%')
				THEN CASE WHEN n.origin = :origin THEN 2 ELSE 1 END ELSE 0 END) AS rank) AS nr
				CROSS JOIN LATERAL (SELECT (CASE WHEN :pinned = true
					AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(COALESCE(r.tags, '[]')) AS t(tag) WHERE t.tag = 'plugin/pinned' OR t.tag LIKE 'plugin/pinned/%')
				THEN CASE WHEN r.origin = :origin THEN 2 ELSE 1 END ELSE 0 END) AS rank) AS rr
				WHERE n.url = r.url AND (:origin = '' OR n.origin = :origin OR n.origin LIKE concat(:origin, '.%'))
					AND ((n.origin = r.origin AND n.modified > r.modified)
						OR (NOT EXISTS (SELECT 1 FROM ref nn WHERE nn.url = n.url AND nn.origin = n.origin AND nn.modified > n.modified)
							AND (nr.rank > rr.rank OR (nr.rank = rr.rank AND n.modified > r.modified))))),
			'cascade', CASE WHEN jsonb_array_length(COALESCE(r.sources, '[]')) > 0 THEN true END,
			'ignored', CASE WHEN r.metadata->>'ignored' = 'true' THEN true END
		))
		WHERE EXISTS (SELECT * from rows WHERE r.url = rows.url AND r.origin = rows.origin AND r.modified = rows.modified)
		""";

	/**
	 * Responses, plugin counts and cascade are not tracked when the
	 * "no-metadata" profile is active. Expanded tags, obsolete and ignored are kept.
	 */
	private static final String BACKFILL_NO_METADATA = """
		WITH rows as (
			SELECT url, origin, modified from ref
			WHERE (metadata IS NULL OR metadata->>'regen' = 'true')
			AND (:origin = '' OR origin = :origin OR origin LIKE concat(:origin, '.%'))
			LIMIT :batchSize
		)
		UPDATE ref r
		SET metadata = jsonb_strip_nulls(jsonb_build_object(
			'modified', COALESCE(r.metadata->>'modified', to_char(NOW(), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')),
			'expandedTags', (SELECT jsonb_agg(DISTINCT x.tag) FROM jsonb_array_elements_text(COALESCE(r.tags, '[]')) AS t(tag)
				CROSS JOIN LATERAL (SELECT t.tag UNION SELECT left(t.tag, g.n - 1) FROM generate_series(1, length(t.tag)) AS g(n) WHERE substr(t.tag, g.n, 1) = '/') AS x(tag)),
			'obsolete', EXISTS (SELECT 1 FROM ref n
				CROSS JOIN LATERAL (SELECT (CASE WHEN :pinned = true
					AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(COALESCE(n.tags, '[]')) AS t(tag) WHERE t.tag = 'plugin/pinned' OR t.tag LIKE 'plugin/pinned/%')
				THEN CASE WHEN n.origin = :origin THEN 2 ELSE 1 END ELSE 0 END) AS rank) AS nr
				CROSS JOIN LATERAL (SELECT (CASE WHEN :pinned = true
					AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(COALESCE(r.tags, '[]')) AS t(tag) WHERE t.tag = 'plugin/pinned' OR t.tag LIKE 'plugin/pinned/%')
				THEN CASE WHEN r.origin = :origin THEN 2 ELSE 1 END ELSE 0 END) AS rank) AS rr
				WHERE n.url = r.url AND (:origin = '' OR n.origin = :origin OR n.origin LIKE concat(:origin, '.%'))
					AND ((n.origin = r.origin AND n.modified > r.modified)
						OR (NOT EXISTS (SELECT 1 FROM ref nn WHERE nn.url = n.url AND nn.origin = n.origin AND nn.modified > n.modified)
							AND (nr.rank > rr.rank OR (nr.rank = rr.rank AND n.modified > r.modified))))),
			'ignored', CASE WHEN r.metadata->>'ignored' = 'true' THEN true END
		))
		WHERE EXISTS (SELECT * from rows WHERE r.url = rows.url AND r.origin = rows.origin AND r.modified = rows.modified)
		""";

	@PersistenceContext
	private EntityManager em;

	@Autowired
	PluginRepository pluginRepository;

	@Value("#{environment.matchesProfiles('no-metadata')}")
	boolean noMetadata;

	@Value("#{environment.matchesProfiles('archive')}")
	boolean archive;

	/**
	 * Refs are only ranked by plugin/pinned if the plugin is installed in the root origin.
	 * In archive mode a blank current version is a tombstone and is treated as missing.
	 */
	private boolean pinned(String origin) {
		return pluginRepository.findByTagAndOrigin("plugin/pinned", origin)
			.filter(p -> !archive || !isBlank(p))
			.isPresent();
	}

	@Override
	public int backfillMetadata(String origin, int batchSize) {
		em.flush();
		int updated = em.createNativeQuery(noMetadata ? BACKFILL_NO_METADATA : BACKFILL_METADATA)
			.setParameter("origin", origin)
			.setParameter("batchSize", batchSize)
			.setParameter("pinned", pinned(origin))
			.executeUpdate();
		em.flush();
		em.clear();
		return updated;
	}
}
