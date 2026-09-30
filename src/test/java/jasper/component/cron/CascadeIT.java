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
import static org.springframework.test.util.AopTestUtils.getTargetObject;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@IntegrationTest
public class CascadeIT {

	@Autowired
	Cascade cascade;

	@Autowired
	Meta meta;

	@Autowired
	RefRepository refRepository;

	Messages messages;
	Messages mockMessages;

	static final String URL = "https://www.example.com/";

	@BeforeEach
	void init() {
		refRepository.deleteAll();
		Cascade target = getTargetObject(cascade);
		messages = (Messages) getField(target, "messages");
		setField(target, "messages", mockMessages = mock(Messages.class));
	}

	@AfterEach
	void cleanup() {
		Cascade target = getTargetObject(cascade);
		setField(target, "messages", messages);
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

	Ref saveChild(String... sources) {
		var child = new Ref();
		child.setUrl(URL + "child");
		child.setTitle("Child");
		child.setSources(List.of(sources));
		child.setTags(List.of("+user/tester", "plugin/comment"));
		child.setMetadata(Metadata.builder().build());
		return refRepository.save(child);
	}

	@Test
	void testSourcesAreUpdatedSynchronouslyWithoutCascadeWork() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		saveSource(URL + "c");
		saveSource(URL + "d");
		var child = saveChild(URL + "a", URL + "b", URL + "c", URL + "d");
		meta.sources("", child, null);
		assertThat(refRepository.getRefCascade("")).isEmpty();

		cascade.cascadeOrigin("");

		for (var url : List.of(URL + "a", URL + "b", URL + "c", URL + "d")) {
			var source = refRepository.findOneByUrlAndOrigin(url, "").orElseThrow();
			assertThat(source.getMetadata().getResponses()).containsExactly(URL + "child");
			assertThat(source.getMetadata().getPlugins()).containsEntry("plugin/comment", 1L);
			assertThat(source.getMetadata().isObsolete()).isTrue();
		}
		assertThat(refRepository.findOneByUrlAndOrigin(URL + "child", "").orElseThrow()
			.getMetadata().isCascade()).isFalse();
		assertThat(refRepository.getRefCascade("")).isEmpty();
	}

	@Test
	void testCascadeRemovesUncitedSources() {
		for (var url : List.of(URL + "a", URL + "b", URL + "c", URL + "d")) {
			var source = saveSource(url);
			source.getMetadata().setResponses(List.of(URL + "child"));
			refRepository.save(source);
		}
		var child = saveChild(URL + "a");
		child.getMetadata().setCascade(true);
		refRepository.save(child);

		cascade.cascadeOrigin("");

		for (var url : List.of(URL + "b", URL + "c", URL + "d")) {
			assertThat(refRepository.findOneByUrlAndOrigin(url, "").orElseThrow()
				.getMetadata().getResponses()).isNullOrEmpty();
			verify(mockMessages, atLeastOnce()).updateMetadata(argThat(r -> r.getUrl().equals(url)));
		}
		assertThat(refRepository.findOneByUrlAndOrigin(URL + "a", "").orElseThrow()
			.getMetadata().getResponses()).containsExactly(URL + "child");
		assertThat(refRepository.getRefCascade("")).isEmpty();
	}

	@Test
	void testCascadeRetriesWhenRefModified() {
		saveSource(URL + "a");
		saveSource(URL + "b");
		saveSource(URL + "c");
		var child = saveChild(URL + "a", URL + "b", URL + "c");
		meta.sources("", child, null);
		child.getMetadata().setCascade(true);
		refRepository.save(child);
		var stale = refRepository.findOneByUrlAndOrigin(URL + "child", "").orElseThrow();

		assertThat(refRepository.clearCascade(URL + "child", "", stale.getModified().minusSeconds(1))).isEqualTo(0);
		assertThat(refRepository.getRefCascade("")).isPresent();
		assertThat(refRepository.clearCascade(URL + "child", "", stale.getModified())).isEqualTo(1);
		assertThat(refRepository.getRefCascade("")).isEmpty();
	}
}
