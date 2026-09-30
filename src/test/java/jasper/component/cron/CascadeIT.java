package jasper.component.cron;

import jasper.IntegrationTest;
import jasper.component.Messages;
import jasper.component.Meta;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@IntegrationTest
public class CascadeIT {

	@Autowired
	Cascade cascade;

	@Autowired
	Meta meta;

	@Autowired
	RefRepository refRepository;

	Messages messages;

	static final String URL = "https://www.example.com/";

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		messages = cascade.messages;
		cascade.messages = mock(Messages.class);
	}

	@AfterEach
	void cleanup() {
		cascade.messages = messages;
		refRepository.deleteAll();
	}

	Ref saveSource(String url) {
		var source = new Ref();
		source.setUrl(url);
		source.setTitle("Source");
		source.setTags(List.of("+user/tester"));
		source.setMetadata(Metadata.builder()
			.modified("2026-01-01T00:00:00Z")
			.obsolete(true)
			.build());
		return refRepository.save(source);
	}

	@Test
	void testCascadeUpdatesDeferredSources() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		saveSource(URL + "c");
		saveSource(URL + "d");
		var child = new Ref();
		child.setUrl(URL + "child");
		child.setTitle("Child");
		child.setSources(List.of(URL + "a", URL + "b", URL + "c", URL + "d"));
		child.setTags(List.of("+user/tester", "plugin/comment"));
		refRepository.save(child);
		meta.sources("", child, null);

		cascade.cascadeOrigin("");

		for (var url : List.of(URL + "c", URL + "d")) {
			var source = refRepository.findOneByUrlAndOrigin(url, "").orElseThrow();
			assertThat(source.getMetadata().isNewResponse()).isFalse();
			assertThat(source.getMetadata().getResponses()).containsExactly(URL + "child");
			assertThat(source.getMetadata().getPlugins()).containsEntry("plugin/comment", 1L);
			assertThat(source.getMetadata().isObsolete()).isTrue();
			assertThat(source.getMetadata().getModified()).isNotEqualTo("2026-01-01T00:00:00Z");
			verify(cascade.messages, atLeastOnce()).updateMetadata(argThat(r -> r.getUrl().equals(url)));
		}
		assertThat(refRepository.getRefNewResponse("")).isEmpty();
	}

	@Test
	void testCascadeRemovesDeletedResponse() {
		var source = saveSource(URL + "c");
		source.getMetadata().setResponses(List.of(URL + "child"));
		source.getMetadata().setNewResponse(true);
		refRepository.save(source);

		cascade.cascadeOrigin("");

		var result = refRepository.findOneByUrlAndOrigin(URL + "c", "").orElseThrow();
		assertThat(result.getMetadata().isNewResponse()).isFalse();
		assertThat(result.getMetadata().getResponses()).isNullOrEmpty();
		verify(cascade.messages, atLeastOnce()).updateMetadata(argThat(r -> r.getUrl().equals(URL + "c")));
	}
}
