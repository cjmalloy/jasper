package jasper.repository;

import jasper.IntegrationTest;
import jasper.domain.Metadata;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static jasper.component.Meta.expandTags;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@ActiveProfiles({"no-metadata", "test"})
@DisabledIfSystemProperty(named = "spring.profiles.active", matches = ".*sqlite.*")
public class NoMetadataBackfillIT {
	static final String URL = "https://www.example.com/";

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	BackfillRepository backfillRepository;

	@BeforeEach
	void init() {
		refRepository.deleteAllInBatch();
		pluginRepository.deleteAllInBatch();
	}

	Ref ref(String url, String origin, Instant modified, String... sources) {
		var ref = new Ref();
		ref.setUrl(url);
		ref.setOrigin(origin);
		ref.setSources(List.of(sources));
		ref.setTags(List.of("public", "plugin/comment"));
		ref.setMetadata(Metadata.builder().expandedTags(expandTags(ref.getTags())).build());
		ref.setModified(modified);
		return ref;
	}

	@Test
	void testRegenSkipsResponses() {
		var plugin = new Plugin();
		plugin.setTag("plugin/comment");
		plugin.setOrigin("");
		pluginRepository.save(plugin);
		var now = Instant.now();
		refRepository.save(ref(URL + "source", "", now));
		refRepository.save(ref(URL + "response", "", now.plusMillis(1), URL + "source"));
		refRepository.save(ref(URL + "response", "@other", now.minusSeconds(10), URL + "source"));
		refRepository.dropMetadata("");

		while (backfillRepository.backfillMetadata("", 10) > 0);

		var source = refRepository.findOneByUrlAndOrigin(URL + "source", "").orElseThrow();
		assertThat(source.getMetadata()).isNotNull();
		assertThat(source.getMetadata().getModified()).isNotNull();
		assertThat(source.getMetadata().getResponses()).isNullOrEmpty();
		assertThat(source.getMetadata().getInternalResponses()).isNullOrEmpty();
		assertThat(source.getMetadata().getPlugins()).isNullOrEmpty();
		var response = refRepository.findOneByUrlAndOrigin(URL + "response", "").orElseThrow();
		assertThat(response.getMetadata().isCascade()).isFalse();
		assertThat(response.getMetadata().isObsolete()).isFalse();
		assertThat(response.getMetadata().getExpandedTags()).containsExactlyInAnyOrder("public", "plugin", "plugin/comment");
		assertThat(refRepository.findOneByUrlAndOrigin(URL + "response", "@other").orElseThrow().getMetadata().isObsolete())
			.isTrue();
	}
}
