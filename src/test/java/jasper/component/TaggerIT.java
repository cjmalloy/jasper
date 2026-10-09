package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.AopTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@WithMockUser("+user/tester")
@IntegrationTest
public class TaggerIT {

	@Autowired
    Tagger tagger;

	@Autowired
	RefRepository refRepository;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	Ingest ingest;

	static final String URL = "https://www.example.com/";

	Ref refWithTags(String url, String... tags) {
		var ref = new Ref();
		ref.setUrl(url);
		ref.setTags(new ArrayList<>(List.of(tags)));
		refRepository.save(ref);
		return ref;
	}

	Ref remoteRefWithTags(String url, String origin, String... tags) {
		var ref = new Ref();
		ref.setUrl(url);
		ref.setOrigin(origin);
		ref.setTags(new ArrayList<>(List.of(tags)));
		refRepository.save(ref);
		return ref;
	}

	@BeforeEach
	void init() {
		refRepository.deleteAll();
	}

	@Test
	void testDebugRefWriteAttachesLogs() {
		tagger.tag(URL, "", "+plugin/debug");

		var logs = refRepository.findAll().stream()
			.filter(r -> r.hasTag("+plugin/log"))
			.toList();
		assertThat(logs).hasSize(1);
		assertThat(logs.getFirst().getSources()).containsExactly(URL);
		assertThat(logs.getFirst().getTitle()).isEqualTo("+plugin/debug Ingest create");
		assertThat(logs.getFirst().getComment()).contains("jasper.component.Tagger.tag");
	}

	@Test
	void testNonDebugRefWriteDoesNotAttachLogs() {
		tagger.tag(URL, "", "test");

		assertThat(refRepository.findAll().stream().filter(r -> r.hasTag("+plugin/log")))
			.isEmpty();
	}

	@Test
	void testTagRef() {
		tagger.tag(URL, "", "test");

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTags())
			.contains("test");
	}

	@Test
	void testTagExistingRef() {
		refWithTags(URL);

		tagger.tag(URL, "", "test");

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTags())
			.contains("test");
	}

	@Test
	void testTagRemoteRef() {
		tagger.tag(URL, "@other", "test");

		assertThat(refRepository.existsByUrlAndOrigin(URL, "@other"))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "@other").get();
		assertThat(fetched.getTags())
			.contains("test");
	}

	@Test
	void testTagExistingRemoteRef() {
		remoteRefWithTags(URL, "@other");

		tagger.tag(URL, "@other", "test");

		assertThat(refRepository.existsByUrlAndOrigin(URL, "@other"))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "@other").get();
		assertThat(fetched.getTags())
			.contains("test");
	}

	@Test
	void testSilentPluginRef() {
		tagger.silentPlugin(URL, "Test", "", "plugin/test", objectMapper.createObjectNode());

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTags())
			.contains("plugin/test");
	}

	@Test
	void testSilentPluginExistingRef() {
		refWithTags(URL);

		tagger.silentPlugin(URL, "Test", "", "plugin/test", objectMapper.createObjectNode());

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "").get();
		assertThat(fetched.getTags())
			.contains("plugin/test");
	}

	@Test
	void testSilentPluginRemoteRef() {
		tagger.silentPlugin(URL, "Test", "@other", "plugin/test", objectMapper.createObjectNode());

		assertThat(refRepository.existsByUrlAndOrigin(URL, "@other"))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "@other").get();
		assertThat(fetched.getTags())
			.contains("plugin/test");
	}

	@Test
	void testSilentPluginRemoteRefMultiple() {
		tagger.silentPlugin(URL + 1, "Test", "@other", "plugin/test", objectMapper.createObjectNode());
		tagger.silentPlugin(URL + 2, "Test", "@other", "plugin/test", objectMapper.createObjectNode());
		tagger.silentPlugin(URL + 3, "Test", "@other", "plugin/test", objectMapper.createObjectNode());
		tagger.silentPlugin(URL + 4, "Test", "@other", "plugin/test", objectMapper.createObjectNode());

		assertThat(refRepository.existsByUrlAndOrigin(URL + 1, "@other"))
			.isTrue();
		assertThat(refRepository.existsByUrlAndOrigin(URL + 2, "@other"))
			.isTrue();
		assertThat(refRepository.existsByUrlAndOrigin(URL + 3, "@other"))
			.isTrue();
		assertThat(refRepository.existsByUrlAndOrigin(URL + 4, "@other"))
			.isTrue();
		var fetched1 = refRepository.findOneByUrlAndOrigin(URL + 1, "@other").get();
		var fetched2 = refRepository.findOneByUrlAndOrigin(URL + 2, "@other").get();
		var fetched3 = refRepository.findOneByUrlAndOrigin(URL + 3, "@other").get();
		var fetched4 = refRepository.findOneByUrlAndOrigin(URL + 4, "@other").get();
		assertThat(fetched1.getTags())
			.contains("plugin/test");
		assertThat(fetched2.getTags())
			.contains("plugin/test");
		assertThat(fetched3.getTags())
			.contains("plugin/test");
		assertThat(fetched4.getTags())
			.contains("plugin/test");
	}

	@Test
	void testSilentPluginEmptyOriginDoesNotMoveCursor() {
		tagger.silentPlugin(URL + 1, "Test", "@other", "plugin/test", objectMapper.createObjectNode());
		tagger.silentPlugin(URL + 2, "Test", "@other", "plugin/test", objectMapper.createObjectNode());

		assertThat(refRepository.getCursor("@other"))
			.isNull();
		assertThat(refRepository.findOneByUrlAndOrigin(URL + 2, "@other").get().getMetadata().isIgnored())
			.isTrue();
	}

	@Test
	void testSilentPluginIgnoredUntilOverwritten() {
		remoteRefWithTags(URL + 1, "@other");
		var cursor = refRepository.getCursor("@other");
		tagger.silentPlugin(URL + 2, "Test", "@other", "plugin/test", objectMapper.createObjectNode());

		assertThat(refRepository.getCursor("@other"))
			.isEqualTo(cursor);

		var pulled = new Ref();
		pulled.setUrl(URL + 2);
		pulled.setOrigin("@other");
		pulled.setTags(new ArrayList<>(List.of("test")));
		pulled.setModified(Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MILLIS));
		ingest.push("@other", pulled, false, false);

		var fetched = refRepository.findOneByUrlAndOrigin(URL + 2, "@other").get();
		assertThat(fetched.getMetadata().isIgnored())
			.isFalse();
		assertThat(refRepository.getCursor("@other"))
			.isEqualTo(pulled.getModified());
	}

	@Test
	void testSilentPluginExistingRemoteRef() {
		remoteRefWithTags(URL, "@other");

		tagger.silentPlugin(URL, "Test", "@other", "plugin/test", objectMapper.createObjectNode());

		assertThat(refRepository.existsByUrlAndOrigin(URL, "@other"))
			.isTrue();
		var fetched = refRepository.findOneByUrlAndOrigin(URL, "@other").get();
		assertThat(fetched.getTags())
			.contains("plugin/test");
	}

	@Test
	void testRemoveAllResponsesSkipsSubOrigins() {
		refWithTags(URL);
		for (var origin : List.of("", "@sub")) {
			var res = new Ref();
			res.setUrl("tag:/user/tester?url=" + URL);
			res.setOrigin(origin);
			res.setSources(new ArrayList<>(List.of(URL)));
			res.setTags(new ArrayList<>(List.of("internal", "+user/tester", "+plugin/user/run")));
			refRepository.save(res);
		}
		var other = new Ref();
		other.setUrl("tag:/user/other?url=" + URL);
		other.setOrigin("@sub");
		other.setSources(new ArrayList<>(List.of(URL)));
		other.setTags(new ArrayList<>(List.of("internal", "+user/other", "+plugin/user/run")));
		refRepository.save(other);

		((Tagger) AopTestUtils.getUltimateTargetObject(tagger)).removeAllResponses(URL, "", "+plugin/user/run");

		assertThat(refRepository.findOneByUrlAndOrigin("tag:/user/tester?url=" + URL, "").get().getTags())
			.doesNotContain("+plugin/user/run");
		assertThat(refRepository.findOneByUrlAndOrigin("tag:/user/tester?url=" + URL, "@sub").get().getTags())
			.contains("+plugin/user/run");
		assertThat(refRepository.findOneByUrlAndOrigin("tag:/user/other?url=" + URL, "@sub").get().getTags())
			.contains("+plugin/user/run");
		assertThat(refRepository.findOneByUrlAndOrigin("tag:/user/other?url=" + URL, ""))
			.isEmpty();
	}

}
