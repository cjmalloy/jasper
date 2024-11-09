package jasper.component;

import jasper.IntegrationTest;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

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

}
