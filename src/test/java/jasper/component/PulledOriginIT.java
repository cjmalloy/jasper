package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.IntegrationTest;
import jasper.domain.Ext;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.errors.ReadOnlyOriginException;
import jasper.repository.ExtRepository;
import jasper.repository.RefRepository;
import jasper.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
public class PulledOriginIT {

	@Autowired
	Ingest ingest;

	@Autowired
	IngestExt ingestExt;

	@Autowired
	IngestUser ingestUser;

	@Autowired
	Tagger tagger;

	@Autowired
	ConfigCache configs;

	@Autowired
	RefRepository refRepository;

	@Autowired
	ExtRepository extRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ObjectMapper objectMapper;

	static final String URL = "https://www.example.com/";
	static final String PULLED = "@pulled";

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		extRepository.deleteAll();
		userRepository.deleteAll();
		var remote = new Ref();
		remote.setUrl("https://remote.example.com");
		remote.setOrigin("");
		var tags = List.of("+plugin/origin", "+plugin/origin/pull");
		remote.setTags(new ArrayList<>(tags));
		remote.setMetadata(Metadata.builder().expandedTags(tags).build());
		remote.setPlugins(objectMapper.createObjectNode()
			.set("+plugin/origin", objectMapper.createObjectNode().put("local", PULLED)));
		refRepository.save(remote);
		configs.clearConfigCache();
	}

	Ref ref(String origin) {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setOrigin(origin);
		ref.setTags(new ArrayList<>(List.of("public")));
		return ref;
	}

	@Test
	void testPulled() {
		assertThat(configs.pulled(PULLED)).isTrue();
		assertThat(configs.pulled("")).isFalse();
		assertThat(configs.pulled("@other")).isFalse();
	}

	@Test
	void testCreateRefInPulledOriginFails() {
		assertThatThrownBy(() -> ingest.create("", ref(PULLED)))
			.isInstanceOf(ReadOnlyOriginException.class);
		assertThat(refRepository.existsByUrlAndOrigin(URL, PULLED)).isFalse();
	}

	@Test
	void testCreateRefInOtherOriginSucceeds() {
		ingest.create("", ref("@other"));

		assertThat(refRepository.existsByUrlAndOrigin(URL, "@other")).isTrue();
	}

	@Test
	void testUpdateRefInPulledOriginFails() {
		var modified = Instant.now().minusSeconds(60).truncatedTo(MILLIS);
		var ref = ref(PULLED);
		ref.setModified(modified);
		ingest.push("", ref, false, false);
		var update = refRepository.findOneByUrlAndOrigin(URL, PULLED).get();
		update.setTitle("Edited");

		assertThatThrownBy(() -> ingest.update("", update))
			.isInstanceOf(ReadOnlyOriginException.class);
		assertThat(refRepository.getCursor(PULLED)).isEqualTo(modified);
	}

	@Test
	void testReplicationAndDeleteInPulledOriginSucceed() {
		var ref = ref(PULLED);
		ref.setModified(Instant.now().minusSeconds(60).truncatedTo(MILLIS));
		ingest.push("", ref, false, false);

		assertThat(refRepository.existsByUrlAndOrigin(URL, PULLED)).isTrue();

		ingest.delete("", URL, PULLED);

		assertThat(refRepository.existsByUrlAndOrigin(URL, PULLED)).isFalse();
	}

	@Test
	void testSilentPluginInPulledOriginSucceeds() {
		var ref = ref(PULLED);
		var modified = Instant.now().minusSeconds(60).truncatedTo(MILLIS);
		ref.setModified(modified);
		ingest.push("", ref, false, false);

		tagger.silentPlugin(URL, "", PULLED, "plugin/test", objectMapper.createObjectNode());

		assertThat(refRepository.findOneByUrlAndOrigin(URL, PULLED).get().getTags()).contains("plugin/test");
		assertThat(refRepository.getCursor(PULLED)).isEqualTo(modified);
	}

	@Test
	void testCreateExtInPulledOriginFails() {
		var ext = new Ext();
		ext.setTag("test");
		ext.setOrigin(PULLED);

		assertThatThrownBy(() -> ingestExt.create(ext))
			.isInstanceOf(ReadOnlyOriginException.class);
	}

	@Test
	void testCreateUserInPulledOriginFails() {
		var user = new User();
		user.setTag("+user/test");
		user.setOrigin(PULLED);

		assertThatThrownBy(() -> ingestUser.create(user))
			.isInstanceOf(ReadOnlyOriginException.class);
	}
}
