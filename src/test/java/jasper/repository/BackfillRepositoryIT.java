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

import java.util.List;

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
				jsonb_array_length(metadata->'plugins'->'plugin/comment'),
				metadata->>'obsolete'
			FROM ref WHERE url = :url AND origin = :origin""")
			.setParameter("url", parent.getUrl())
			.setParameter("origin", parent.getOrigin())
			.getSingleResult();
		assertThat(((Number) counts[0]).intValue()).isEqualTo(1);
		assertThat(((Number) counts[1]).intValue()).isEqualTo(1);
		assertThat(counts[2]).isEqualTo("false");
	}
}
