package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jasper.DisabledOnSqlite;
import jasper.IntegrationTest;
import jasper.component.ConfigCache;
import jasper.domain.Metadata;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class BackfillRepositoryIT {

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	BackfillRepository backfillRepository;

	@Autowired
	ConfigCache configCache;

	@Autowired
	TransactionTemplate transactionTemplate;

	@PersistenceContext
	EntityManager em;

	@BeforeEach
	void init() {
		refRepository.deleteAllInBatch();
		pluginRepository.deleteAllInBatch();
		configCache.clearPluginCache();
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_BackfillsNullMetadata() {
		var plugin = new Plugin();
		plugin.setTag("plugin/comment");
		plugin.setOrigin("");
		pluginRepository.save(plugin);

		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setTags(List.of("public"));
		parent.setMetadata(null);
		refRepository.save(parent);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setTags(List.of("public"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("public"))
			.build());
		refRepository.save(response);

		int updated = backfillRepository.backfillMetadata("", 10);

		assertThat(updated).isGreaterThanOrEqualTo(1);

		var metadataJson = (String) em.createNativeQuery(
			"SELECT metadata FROM ref WHERE url = :url AND origin = :origin")
			.setParameter("url", parent.getUrl())
			.setParameter("origin", parent.getOrigin())
			.getSingleResult();
		assertThat(metadataJson).isNotNull();
		assertThat(metadataJson).contains("\"modified\"");
		assertThat(metadataJson).contains("\"obsolete\"");
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_BackfillsRegenFlag() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setMetadata(Metadata.builder().regen(true).build());
		refRepository.save(parent);

		int updated = backfillRepository.backfillMetadata("", 10);

		assertThat(updated).isEqualTo(1);
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_BlankNewResponseAndNewReactionUseCreated() {
		var created = Instant.parse("2025-03-04T05:06:07.123456Z");
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setCreated(created);
		parent.setMetadata(null);
		refRepository.save(parent);
		var kept = new Ref();
		kept.setUrl("http://example.com/kept");
		kept.setOrigin("");
		kept.setMetadata(Metadata.builder()
			.regen(true)
			.newResponse("2026-01-02T00:00:00Z")
			.newReaction("2026-01-03T00:00:00Z")
			.build());
		refRepository.save(kept);

		backfillRepository.backfillMetadata("", 10);

		var metadata = refRepository.findOneByUrlAndOrigin(parent.getUrl(), "").orElseThrow().getMetadata();
		assertThat(metadata.getNewResponse()).isEqualTo(Metadata.timestamp(created));
		assertThat(metadata.getNewReaction()).isEqualTo(Metadata.timestamp(created));
		var keptMetadata = refRepository.findOneByUrlAndOrigin(kept.getUrl(), "").orElseThrow().getMetadata();
		assertThat(keptMetadata.getNewResponse()).isEqualTo("2026-01-02T00:00:00Z");
		assertThat(keptMetadata.getNewReaction()).isEqualTo("2026-01-03T00:00:00Z");
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_BlankNewResponseAndNewReactionUseLatestResponse() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setCreated(Instant.parse("2025-01-01T00:00:00Z"));
		parent.setMetadata(null);
		refRepository.save(parent);
		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of(parent.getUrl()));
		response.setCreated(Instant.parse("2025-02-01T00:00:00.123456Z"));
		response.setMetadata(Metadata.builder().build());
		refRepository.save(response);
		var vote = new Ref();
		vote.setUrl("tag:/+user/tester?url=" + parent.getUrl());
		vote.setOrigin("");
		vote.setSources(List.of(parent.getUrl()));
		vote.setCreated(Instant.parse("2025-03-01T00:00:00Z"));
		refRepository.save(vote);
		var voteModified = refRepository.findOneByUrlAndOrigin(vote.getUrl(), "").orElseThrow().getModified();

		backfillRepository.backfillMetadata("", 10);

		var metadata = refRepository.findOneByUrlAndOrigin(parent.getUrl(), "").orElseThrow().getMetadata();
		assertThat(metadata.getNewResponse()).isEqualTo(Metadata.timestamp(Instant.parse("2025-02-01T00:00:00.123456Z")));
		assertThat(metadata.getNewReaction()).isEqualTo(Metadata.timestamp(voteModified));
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_RespectsOriginFilter() {
		var ref1 = new Ref();
		ref1.setUrl("http://example.com/ref1");
		ref1.setOrigin("@test");
		ref1.setMetadata(null);
		refRepository.save(ref1);

		var ref2 = new Ref();
		ref2.setUrl("http://example.com/ref2");
		ref2.setOrigin("@other");
		ref2.setMetadata(null);
		refRepository.save(ref2);

		int updated = backfillRepository.backfillMetadata("@test", 10);

		assertThat(updated).isEqualTo(1);
	}

	@Test
	void testBackfillMetadata_ReturnsZeroWhenNothingToBackfill() {
		var ref = new Ref();
		ref.setUrl("http://example.com/ref");
		ref.setOrigin("");
		ref.setMetadata(Metadata.builder().build());
		refRepository.save(ref);

		int updated = backfillRepository.backfillMetadata("", 10);

		assertThat(updated).isEqualTo(0);
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_IgnoresObsoleteResponses() {
		var plugin = new Plugin();
		plugin.setTag("plugin/comment");
		plugin.setOrigin("");
		pluginRepository.save(plugin);

		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setMetadata(null);
		refRepository.save(parent);

		var obsolete = new Ref();
		obsolete.setUrl("http://example.com/response");
		obsolete.setOrigin("@other");
		obsolete.setSources(List.of("http://example.com/parent"));
		obsolete.setTags(List.of("plugin/comment"));
		obsolete.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "plugin"))
			.obsolete(true)
			.build());
		refRepository.save(obsolete);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setTags(List.of("plugin/comment"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "plugin"))
			.build());
		refRepository.save(response);

		backfillRepository.backfillMetadata("", 10);

		var counts = (Object[]) em.createNativeQuery("""
			SELECT
				COALESCE(jsonb_array_length(metadata->'responses'), 0) + COALESCE(jsonb_array_length(metadata->'internalResponses'), 0),
				metadata->>'obsolete'
			FROM ref WHERE url = :url AND origin = :origin""")
			.setParameter("url", parent.getUrl())
			.setParameter("origin", parent.getOrigin())
			.getSingleResult();
		assertThat(((Number) counts[0]).intValue()).isEqualTo(1);
		assertThat(counts[1]).isEqualTo("false");
		var loaded = refRepository.findOneByUrlAndOrigin(parent.getUrl(), parent.getOrigin()).get();
		assertThat(loaded.getMetadata().getPlugins()).containsEntry("plugin/comment", 1L);
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_UserUrlsNeverObsolete() {
		var plugin = new Plugin();
		plugin.setTag("+plugin/user/run");
		plugin.setOrigin("");
		pluginRepository.save(plugin);

		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setMetadata(null);
		refRepository.save(parent);

		for (var origin : List.of("", "@other")) {
			var userUrl = new Ref();
			userUrl.setUrl("tag:/user/tester?url=http://example.com/parent");
			userUrl.setOrigin(origin);
			userUrl.setSources(List.of("http://example.com/parent"));
			userUrl.setTags(List.of("+plugin/user/run"));
			userUrl.setMetadata(Metadata.builder()
				.expandedTags(List.of("+plugin/user/run", "+plugin/user", "+plugin"))
				.obsolete(origin.isEmpty())
				.build());
			refRepository.save(userUrl);
		}

		backfillRepository.backfillMetadata("", 10);

		var loaded = refRepository.findOneByUrlAndOrigin(parent.getUrl(), parent.getOrigin()).get();
		assertThat(loaded.getMetadata().getPlugins()).containsEntry("+plugin/user/run", 1L);
		assertThat(loaded.getMetadata().getRemotePlugins()).containsEntry("+plugin/user/run", 2L);
		assertThat(loaded.getMetadata().getUserUrls())
			.containsEntry("+plugin/user/run", List.of("tag:/user/tester?url=http://example.com/parent"))
			.containsEntry("+plugin/user", List.of("tag:/user/tester?url=http://example.com/parent"));
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_UserUrlsQualifiedWithRootOrigin() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("@test");
		parent.setMetadata(null);
		refRepository.save(parent);

		for (var origin : List.of("@test", "@test.other")) {
			var userUrl = new Ref();
			userUrl.setUrl("tag:/user/tester?url=http://example.com/parent");
			userUrl.setOrigin(origin);
			userUrl.setSources(List.of("http://example.com/parent"));
			userUrl.setTags(List.of("+plugin/user/run"));
			userUrl.setMetadata(Metadata.builder()
				.expandedTags(List.of("+plugin/user/run", "+plugin/user", "+plugin"))
				.build());
			refRepository.save(userUrl);
		}

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("@test");
		response.setSources(List.of("http://example.com/parent"));
		response.setTags(List.of("+plugin/user/run"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("+plugin/user/run", "+plugin/user", "+plugin"))
			.build());
		refRepository.save(response);

		backfillRepository.backfillMetadata("@test", 10);

		var loaded = refRepository.findOneByUrlAndOrigin(parent.getUrl(), parent.getOrigin()).get();
		assertThat(loaded.getMetadata().getUserUrls().get("+plugin/user/run"))
			.containsExactlyInAnyOrder(
				"tag:/user/tester@test?url=http://example.com/parent",
				"http://example.com/response@test");
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_NormalizesProtectedUserTag() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("@a");
		parent.setMetadata(Metadata.builder().regen(true).build());
		refRepository.save(parent);

		var userUrl = new Ref();
		userUrl.setUrl("tag:/+user/tester?url=http://example.com/parent");
		userUrl.setOrigin("@a");
		userUrl.setSources(List.of("http://example.com/parent"));
		userUrl.setTags(List.of("+plugin/user/run"));
		refRepository.save(userUrl);

		backfillRepository.backfillMetadata("@a", 10);

		var loaded = refRepository.findOneByUrlAndOrigin(parent.getUrl(), parent.getOrigin()).get();
		assertThat(loaded.getMetadata().getUserUrls().get("+plugin/user/run"))
			.containsExactly("tag:/user/tester@a?url=http://example.com/parent");
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_KeepsUserUrlsFromOtherOrigins() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("@a");
		parent.setMetadata(Metadata.builder()
			.regen(true)
			.userUrls(Map.of("+plugin/user/run", List.of(
				"tag:/user/tester@a?url=http://example.com/parent",
				"tag:/user/deleted?url=http://example.com/parent")))
			.build());
		refRepository.save(parent);

		var userUrl = new Ref();
		userUrl.setUrl("tag:/user/tester?url=http://example.com/parent");
		userUrl.setOrigin("");
		userUrl.setSources(List.of("http://example.com/parent"));
		userUrl.setTags(List.of("+plugin/user/run"));
		userUrl.setMetadata(Metadata.builder()
			.expandedTags(List.of("+plugin/user/run", "+plugin/user", "+plugin"))
			.build());
		refRepository.save(userUrl);

		backfillRepository.backfillMetadata("", 10);

		var loaded = refRepository.findOneByUrlAndOrigin(parent.getUrl(), parent.getOrigin()).get();
		assertThat(loaded.getMetadata().getUserUrls().get("+plugin/user/run"))
			.containsExactlyInAnyOrder(
				"tag:/user/tester?url=http://example.com/parent",
				"tag:/user/tester@a?url=http://example.com/parent");
		assertThat(loaded.getMetadata().getUserUrls().get("+plugin/user"))
			.containsExactly("tag:/user/tester?url=http://example.com/parent");
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_UserUrlWithoutMetadata() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setMetadata(null);
		refRepository.save(parent);

		var userUrl = new Ref();
		userUrl.setUrl("tag:/user/tester?url=http://example.com/parent");
		userUrl.setOrigin("");
		userUrl.setSources(List.of("http://example.com/parent"));
		userUrl.setTags(List.of("+plugin/user/run"));
		userUrl.setMetadata(null);
		refRepository.save(userUrl);

		backfillRepository.backfillMetadata("", 10);

		var loaded = refRepository.findOneByUrlAndOrigin(parent.getUrl(), parent.getOrigin()).get();
		assertThat(loaded.getMetadata().getPlugins()).containsEntry("+plugin/user/run", 1L);
		assertThat(loaded.getMetadata().getRemotePlugins()).containsEntry("+plugin/user/run", 1L);
		assertThat(loaded.getMetadata().getUserUrls()).containsEntry("+plugin/user/run", List.of("tag:/user/tester?url=http://example.com/parent"));
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_ObsoleteNonUserTagUrlNotCounted() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setMetadata(null);
		refRepository.save(parent);

		for (var origin : List.of("", "@other")) {
			var response = new Ref();
			response.setUrl("tag:/news?url=http://example.com/parent");
			response.setOrigin(origin);
			response.setSources(List.of("http://example.com/parent"));
			response.setTags(List.of("plugin/comment"));
			response.setMetadata(Metadata.builder()
				.expandedTags(List.of("plugin/comment", "plugin"))
				.obsolete(origin.isEmpty())
				.build());
			refRepository.save(response);
		}

		backfillRepository.backfillMetadata("", 10);

		var loaded = refRepository.findOneByUrlAndOrigin(parent.getUrl(), parent.getOrigin()).get();
		assertThat(loaded.getMetadata().getPlugins()).isNullOrEmpty();
		assertThat(loaded.getMetadata().getRemotePlugins()).containsEntry("plugin/comment", 1L);
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_LocalPlugins() {
		var plugin = new Plugin();
		plugin.setTag("plugin/comment");
		plugin.setOrigin("");
		pluginRepository.save(plugin);

		var remoteOnly = new Ref();
		remoteOnly.setUrl("http://example.com/remoteOnly");
		remoteOnly.setOrigin("");
		remoteOnly.setMetadata(null);
		refRepository.save(remoteOnly);

		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setMetadata(null);
		refRepository.save(parent);

		var remote = new Ref();
		remote.setUrl("http://example.com/remote");
		remote.setOrigin("@other");
		remote.setSources(List.of("http://example.com/parent", "http://example.com/remoteOnly"));
		remote.setTags(List.of("plugin/comment"));
		remote.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "plugin"))
			.build());
		refRepository.save(remote);

		var local = new Ref();
		local.setUrl("http://example.com/local");
		local.setOrigin("");
		local.setSources(List.of("http://example.com/parent"));
		local.setTags(List.of("plugin/comment"));
		local.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "plugin"))
			.build());
		refRepository.save(local);

		backfillRepository.backfillMetadata("", 10);

		var loaded = refRepository.findOneByUrlAndOrigin(remoteOnly.getUrl(), remoteOnly.getOrigin()).get();
		assertThat(loaded.getMetadata().getPlugins()).isNullOrEmpty();
		assertThat(loaded.getMetadata().getRemotePlugins()).containsEntry("plugin/comment", 1L);
		assertThat(loaded.hasPluginResponse("plugin/comment")).isFalse();

		loaded = refRepository.findOneByUrlAndOrigin(parent.getUrl(), parent.getOrigin()).get();
		assertThat(loaded.getMetadata().getPlugins()).containsEntry("plugin/comment", 1L);
		assertThat(loaded.getMetadata().getRemotePlugins()).containsEntry("plugin/comment", 2L);
		assertThat(loaded.hasPluginResponse("plugin/comment")).isTrue();
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_AssumesUnbackfilledResponsesObsolete() {
		var plugin = new Plugin();
		plugin.setTag("plugin/comment");
		plugin.setOrigin("");
		pluginRepository.save(plugin);

		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setMetadata(null);
		refRepository.save(parent);

		var obsolete = new Ref();
		obsolete.setUrl("http://example.com/response");
		obsolete.setOrigin("");
		obsolete.setSources(List.of("http://example.com/parent"));
		obsolete.setTags(List.of("plugin/comment"));
		obsolete.setModified(Instant.now().minusSeconds(60));
		obsolete.setMetadata(null);
		refRepository.save(obsolete);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("@other");
		response.setSources(List.of("http://example.com/parent"));
		response.setTags(List.of("plugin/comment"));
		response.setModified(Instant.now());
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "plugin"))
			.build());
		refRepository.save(response);

		while (backfillRepository.backfillMetadata("", 1) > 0);

		assertParentCounts(parent, 1, 1);
		var obsoleteFlag = em.createNativeQuery("""
			SELECT metadata->>'obsolete' FROM ref WHERE url = :url AND origin = :origin""")
			.setParameter("url", obsolete.getUrl())
			.setParameter("origin", obsolete.getOrigin())
			.getSingleResult();
		assertThat(obsoleteFlag).isEqualTo("true");
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_AssumesLegacyObsoleteCountObsolete() {
		var plugin = new Plugin();
		plugin.setTag("plugin/comment");
		plugin.setOrigin("");
		pluginRepository.save(plugin);

		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setMetadata(null);
		refRepository.save(parent);

		var obsolete = new Ref();
		obsolete.setUrl("http://example.com/response");
		obsolete.setOrigin("");
		obsolete.setSources(List.of("http://example.com/parent"));
		obsolete.setTags(List.of("plugin/comment"));
		obsolete.setModified(Instant.now().minusSeconds(60));
		obsolete.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "plugin"))
			.build());
		refRepository.save(obsolete);
		transactionTemplate.executeWithoutResult(status -> em.createNativeQuery("""
			UPDATE ref SET metadata = jsonb_set(metadata, '{obsolete}', to_jsonb(1))
			WHERE url = :url AND origin = :origin""")
			.setParameter("url", obsolete.getUrl())
			.setParameter("origin", obsolete.getOrigin())
			.executeUpdate());

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("@other");
		response.setSources(List.of("http://example.com/parent"));
		response.setTags(List.of("plugin/comment"));
		response.setModified(Instant.now());
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "plugin"))
			.build());
		refRepository.save(response);

		while (backfillRepository.backfillMetadata("", 10) > 0);

		assertParentCounts(parent, 1, 1);
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_IgnoresResponsesOutsideOrigin() {
		var plugin = new Plugin();
		plugin.setTag("plugin/comment");
		plugin.setOrigin("@test");
		pluginRepository.save(plugin);

		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("@test");
		parent.setMetadata(null);
		refRepository.save(parent);

		var other = new Ref();
		other.setUrl("http://example.com/other");
		other.setOrigin("@other");
		other.setSources(List.of("http://example.com/parent"));
		other.setTags(List.of("plugin/comment"));
		other.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "plugin"))
			.build());
		refRepository.save(other);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("@test");
		response.setSources(List.of("http://example.com/parent"));
		response.setTags(List.of("plugin/comment"));
		response.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "plugin"))
			.build());
		refRepository.save(response);

		backfillRepository.backfillMetadata("@test", 10);

		assertParentCounts(parent, 1, 1);
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_EmptyPluginTable() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setMetadata(null);
		refRepository.save(parent);

		for (var origin : List.of("", "@other")) {
			var response = new Ref();
			response.setUrl("http://example.com/response");
			response.setOrigin(origin);
			response.setSources(List.of("http://example.com/parent"));
			response.setTags(List.of("plugin/comment"));
			response.setMetadata(Metadata.builder()
				.expandedTags(List.of("plugin/comment", "plugin"))
				.build());
			refRepository.save(response);
		}

		backfillRepository.backfillMetadata("", 10);

		var loaded = refRepository.findOneByUrlAndOrigin(parent.getUrl(), parent.getOrigin()).get();
		assertThat(loaded.getMetadata().getPlugins()).containsEntry("plugin/comment", 1L);
		assertThat(loaded.getMetadata().getRemotePlugins()).containsEntry("plugin/comment", 2L);
	}

	@Test
	@DisabledOnSqlite
	void testBackfillMetadata_CascadesRefsWithSources() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		parent.setMetadata(null);
		refRepository.save(parent);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setMetadata(null);
		refRepository.save(response);

		assertThat(backfillRepository.backfillMetadata("", 10)).isEqualTo(2);

		assertThat(cascade(parent)).isNull();
		assertThat(cascade(response)).isEqualTo("true");
		assertThat(refRepository.findOneByUrlAndOrigin(response.getUrl(), response.getOrigin()).get().getMetadata().isCascade()).isTrue();
	}

	private Object cascade(Ref ref) {
		return em.createNativeQuery("""
			SELECT metadata->>'cascade' FROM ref WHERE url = :url AND origin = :origin""")
			.setParameter("url", ref.getUrl())
			.setParameter("origin", ref.getOrigin())
			.getSingleResult();
	}

	private void assertParentCounts(Ref parent, int responses, int comments) {
		var count = (Number) em.createNativeQuery("""
			SELECT COALESCE(jsonb_array_length(metadata->'responses'), 0) + COALESCE(jsonb_array_length(metadata->'internalResponses'), 0)
			FROM ref WHERE url = :url AND origin = :origin""")
			.setParameter("url", parent.getUrl())
			.setParameter("origin", parent.getOrigin())
			.getSingleResult();
		assertThat(count.intValue()).isEqualTo(responses);
		var loaded = refRepository.findOneByUrlAndOrigin(parent.getUrl(), parent.getOrigin()).get();
		var pluginCount = loaded.getMetadata().getRemotePlugins() == null ? 0 : loaded.getMetadata().getRemotePlugins().getOrDefault("plugin/comment", 0L).intValue();
		assertThat(pluginCount).isEqualTo(comments);
	}
}
