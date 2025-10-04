package jasper.service;

import jasper.IntegrationTest;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WithMockUser("+user/tester")
@IntegrationTest
public class TaggingServiceIT {

	@Autowired
	TaggingService taggingService;

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

	@BeforeEach
	void init() {
		refRepository.deleteAll();
	}

	@Test
	void testCreateTagRef() {
		refWithTags(URL, "+user/tester");

		taggingService.create("test", URL, "");

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(URL, "").get();
		assertThat(fetched.getTags())
			.contains("test");
	}

	@Test
	void testCreatePrivateTagRef() {
		refWithTags(URL, "+user/tester");

		assertThatThrownBy(() -> taggingService.create("_test", URL, ""))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(URL, "").get();
		assertThat(fetched.getTags())
			.doesNotContain("_test");
	}

	@Test
	void testDeletePrivateTagRef() {
		refWithTags(URL, "+user/tester", "_test");

		assertThatThrownBy(() -> taggingService.delete("_test", URL, ""))
			.isInstanceOf(AccessDeniedException.class);

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(URL, "").get();
		assertThat(fetched.getTags())
			.contains("_test");
	}

	@Test
	void testCreateTagRefUnauthorized() {
		refWithTags(URL, "public");

		assertThatThrownBy(() -> taggingService.create("test", URL, ""))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	@WithMockUser(value = "+user/tester", roles = {"EDITOR"})
	void testCreateTagRefEditor() {
		refWithTags(URL, "public");

		taggingService.create("test", URL, "");

		assertThat(refRepository.existsByUrlAndOrigin(URL, ""))
			.isTrue();
		var fetched = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(URL, "").get();
		assertThat(fetched.getTags())
			.contains("test");
	}

}
