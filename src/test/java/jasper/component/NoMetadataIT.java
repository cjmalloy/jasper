package jasper.component;

import jasper.IntegrationTest;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.util.AopTestUtils.getTargetObject;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * Simulates the "no-metadata" profile on the shared context.
 */
@IntegrationTest
public class NoMetadataIT {

	@Autowired
	Ingest ingest;

	@Autowired
	Meta meta;

	@Autowired
	RefRepository refRepository;

	static final String URL = "https://www.example.com/";

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		Meta target = getTargetObject(meta);
		setField(target, "noMetadata", true);
	}

	@AfterEach
	void cleanup() {
		Meta target = getTargetObject(meta);
		setField(target, "noMetadata", false);
		refRepository.deleteAll();
	}

	Ref ref(String url, String origin, String... sources) {
		var ref = new Ref();
		ref.setUrl(url);
		ref.setOrigin(origin);
		ref.setSources(new ArrayList<>(List.of(sources)));
		ref.setTags(new ArrayList<>(List.of("public", "plugin/comment")));
		return ref;
	}

	@Test
	void testResponsesNotTracked() {
		ingest.create("", ref(URL + "source", ""));
		ingest.create("", ref(URL + "response", "", URL + "source"));

		var source = refRepository.findOneByUrlAndOrigin(URL + "source", "").orElseThrow();
		assertThat(source.getMetadata().getResponses()).isNullOrEmpty();
		assertThat(source.getMetadata().getPlugins()).isNullOrEmpty();
		var response = refRepository.findOneByUrlAndOrigin(URL + "response", "").orElseThrow();
		assertThat(response.getMetadata().isCascade()).isFalse();
	}

	@Test
	void testExpandedTagsTracked() {
		ingest.create("", ref(URL, ""));

		assertThat(refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow().getMetadata().getExpandedTags())
			.containsExactlyInAnyOrder("public", "plugin/comment", "plugin");
	}

	@Test
	void testObsoleteTracked() throws InterruptedException {
		ingest.create("", ref(URL, "@a"));
		Thread.sleep(1);
		ingest.create("", ref(URL, "@b"));

		assertThat(refRepository.findOneByUrlAndOrigin(URL, "@a").orElseThrow().getMetadata().isObsolete())
			.isTrue();
		assertThat(refRepository.findOneByUrlAndOrigin(URL, "@b").orElseThrow().getMetadata().isObsolete())
			.isFalse();
	}

	@Test
	void testDeleteRevealsShadowedRef() throws InterruptedException {
		ingest.create("", ref(URL, "@a"));
		Thread.sleep(1);
		ingest.create("", ref(URL, "@b"));

		ingest.delete("", URL, "@b");

		assertThat(refRepository.findOneByUrlAndOrigin(URL, "@a").orElseThrow().getMetadata().isObsolete())
			.isFalse();
	}

	@Test
	void testRegenDropsExistingUserUrls() {
		ingest.create("", ref(URL, ""));
		var ref = refRepository.findOneByUrlAndOrigin(URL, "").orElseThrow();
		ref.getMetadata().setUserUrls(new HashMap<>(Map.of("plugin/user/vote/up", List.of("tag:/user/other@other"))));

		meta.regen("", ref);

		assertThat(ref.getMetadata().getUserUrls()).isNullOrEmpty();
		assertThat(ref.getMetadata().getExpandedTags())
			.containsExactlyInAnyOrder("public", "plugin/comment", "plugin");
	}
}
