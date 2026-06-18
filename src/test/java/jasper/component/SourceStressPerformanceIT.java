package jasper.component;

import jasper.IntegrationTest;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import jasper.service.RefService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@WithMockUser(value = "+user/source-stress", roles = {"ADMIN"})
class SourceStressPerformanceIT {

	private static final String ORIGIN = "";
	private static final String PARENT_URL = "https://perf.example.test/source";
	private static final int[] RESPONSE_COUNTS = {4_000, 8_000, 16_000, 32_000};
	private static final int MAX_RESPONSE_COUNT = 32_000;
	private static final int BATCH_SIZE = 250;
	private static final List<String> HOT_PATH_SUGGESTIONS = List.of(
		"Existing parent metadata is used to defer metadata regeneration above 1,000 responses.",
		"Validate.responses executes a response lookup before every parent edit.",
		"Meta.ref response and plugin scans should only run below the deferral threshold.",
		"Metadata response lists are stored on the parent ref, so large parents are rewritten with a regen marker."
	);

	@Autowired
	RefRepository refRepository;

	@Autowired
	RefService refService;

	@Autowired
	ConfigCache configCache;

	@BeforeEach
	void setup() {
		refRepository.deleteAllInBatch();
		configCache.clearUserCache();
		configCache.clearPluginCache();
		configCache.clearTemplateCache();
	}

	@Test
	@Tag("performance")
	@Disabled("Manual performance stress test; run directly to collect timing data.")
	@Timeout(value = 10, unit = TimeUnit.MINUTES)
	void editParentRefWithManyResponses_reportsScaling() {
		setupRefs();
		var results = new ArrayList<Result>();
		for (var responseCount : RESPONSE_COUNTS) {
			assignSources(responseCount);
			var parent = refRepository.findOneByUrlAndOrigin(PARENT_URL, ORIGIN).orElseThrow();

			var start = System.nanoTime();
			parent.setTitle("Edited parent with " + responseCount + " responses");
			refService.update(parent);
			var elapsed = Duration.ofNanos(System.nanoTime() - start);

			results.add(new Result(responseCount, elapsed));
			System.out.printf("Parent ref edit with %,d responses took %d ms%n", responseCount, elapsed.toMillis());
		}

		for (var i = 1; i < results.size(); i++) {
			var previous = results.get(i - 1);
			var current = results.get(i);
			var responseRatio = (double) current.responseCount() / previous.responseCount();
			var durationRatio = (double) current.elapsed().toNanos() / Math.max(1L, previous.elapsed().toNanos());
			System.out.printf(
				"Scaling %,d -> %,d responses: %.2fx responses, %.2fx duration%n",
				previous.responseCount(),
				current.responseCount(),
				responseRatio,
				durationRatio);
		}

		System.out.println("Likely parent-edit hot paths:");
		HOT_PATH_SUGGESTIONS.forEach(suggestion -> System.out.println("- " + suggestion));
		assertThat(results).hasSize(RESPONSE_COUNTS.length);
	}

	private void setupRefs() {
		refRepository.deleteAllInBatch();

		var published = Instant.parse("2026-01-01T00:00:00Z");
		var modified = Instant.parse("2026-01-01T00:00:00Z");
		var parent = new Ref();
		parent.setUrl(PARENT_URL);
		parent.setOrigin(ORIGIN);
		parent.setTitle("Parent source");
		parent.setTags(List.of("public"));
		parent.setPublished(published);
		parent.setCreated(modified);
		parent.setModified(modified);
		parent.setMetadata(Metadata.builder()
			.expandedTags(List.of("public"))
			.build());
		refRepository.saveAndFlush(parent);

		var batch = new ArrayList<Ref>(BATCH_SIZE);
		for (var i = 0; i < MAX_RESPONSE_COUNT; i++) {
			batch.add(response(i, published, modified.plusMillis(i + 1L)));
			if (batch.size() == BATCH_SIZE) {
				refRepository.saveAllAndFlush(batch);
				batch.clear();
			}
		}
		if (!batch.isEmpty()) {
			refRepository.saveAllAndFlush(batch);
		}
	}

	private void assignSources(int responseCount) {
		var responseUrls = responseUrls(responseCount);
		var published = Instant.parse("2026-01-01T00:00:00Z");
		var modified = Instant.parse("2026-01-01T00:00:00Z");
		var batch = new ArrayList<Ref>(BATCH_SIZE);
		for (var i = 0; i < responseCount; i++) {
			var ref = response(i, published, modified.plusMillis(i + 1L));
			ref.setSources(List.of(PARENT_URL));
			batch.add(ref);
			if (batch.size() == BATCH_SIZE) {
				refRepository.saveAllAndFlush(batch);
				batch.clear();
			}
		}
		if (!batch.isEmpty()) {
			refRepository.saveAllAndFlush(batch);
		}

		var parent = refRepository.findOneByUrlAndOrigin(PARENT_URL, ORIGIN).orElseThrow();
		parent.getMetadata().setResponses(responseUrls);
		refRepository.saveAndFlush(parent);
	}

	private Ref response(int index, Instant published, Instant modified) {
		var plugin = "plugin/perf/" + (index % 8);
		var userPlugin = "plugin/user/perf/" + (index % 4);
		var tags = List.of("public", plugin, userPlugin);

		var ref = new Ref();
		ref.setUrl(responseUrl(index));
		ref.setOrigin(ORIGIN);
		ref.setTitle("Response " + index);
		ref.setTags(tags);
		ref.setPublished(published);
		ref.setCreated(modified);
		ref.setModified(modified);
		ref.setMetadata(Metadata.builder()
			.expandedTags(Meta.expandTags(tags))
			.build());
		return ref;
	}

	private List<String> responseUrls(int responseCount) {
		var urls = new ArrayList<String>(responseCount);
		for (var i = 0; i < responseCount; i++) {
			urls.add(responseUrl(i));
		}
		return urls;
	}

	private String responseUrl(int index) {
		return "https://perf.example.test/response/" + index;
	}

	private record Result(int responseCount, Duration elapsed) {
	}
}
