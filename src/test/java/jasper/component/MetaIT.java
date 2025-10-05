package jasper.component;

import jasper.IntegrationTest;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Transactional
public class MetaIT {

	@Autowired
	Meta meta;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	static final String URL = "https://www.example.com/";

	@Test
	void testCreateMetadata() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("+user/tester"));

		meta.ref("", ref);

		assertThat(ref.getMetadata().getResponses()).isEmpty();
		assertThat(ref.getMetadata().getInternalResponses()).isEmpty();
		assertThat(ref.getMetadata().getPlugins()).isEmpty();
		assertThat(ref.getMetadata().getUserUrls()).isEmpty();
	}

	@Test
	void testCreateMetadataResponse() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("+user/tester"));
		refRepository.save(ref);
		var child = new Ref();
		child.setUrl(URL + 2);
		child.setTitle("Child");
		child.setSources(List.of(URL));
		child.setTags(List.of("+user/tester"));

		meta.sources("", child, null);

		var parent = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(URL, "");
		assertThat(parent).isNotEmpty();
		assertThat(parent.get().getMetadata().getResponses()).containsExactly(URL+2);
		assertThat(parent.get().getMetadata().getInternalResponses()).isNullOrEmpty();
		assertThat(parent.get().getMetadata().getPlugins()).isNullOrEmpty();
		assertThat(parent.get().getMetadata().getUserUrls()).isNullOrEmpty();
	}

	@Test
	void testCreateMetadataInternalResponse() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("+user/tester"));
		refRepository.save(ref);
		var child = new Ref();
		child.setUrl(URL + 2);
		child.setTitle("Child");
		child.setSources(List.of(URL));
		child.setTags(List.of("+user/tester", "internal"));

		meta.sources("", child, null);

		var parent = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(URL, "");
		assertThat(parent).isNotEmpty();
		assertThat(parent.get().getMetadata().getResponses()).isNullOrEmpty();
		assertThat(parent.get().getMetadata().getInternalResponses()).containsExactly(URL+2);
		assertThat(parent.get().getMetadata().getPlugins()).isNullOrEmpty();
		assertThat(parent.get().getMetadata().getUserUrls()).isNullOrEmpty();
	}

	@Test
	void testCreateMetadataPluginResponse() {
		var ref = new Ref();
		ref.setUrl(URL);
		ref.setTitle("First");
		ref.setTags(List.of("+user/tester"));
		refRepository.save(ref);
		var comment = new Plugin();
		comment.setTag("plugin/comment");
		pluginRepository.save(comment);
		var child = new Ref();
		child.setUrl(URL + 2);
		child.setTitle("Child");
		child.setSources(List.of(URL));
		child.setTags(List.of("+user/tester", "plugin/comment", "internal"));

		meta.sources("", child, null);

		var parent = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(URL, "");
		assertThat(parent).isNotEmpty();
		assertThat(parent.get().getMetadata().getResponses()).isEmpty();
		assertThat(parent.get().getMetadata().getInternalResponses()).containsExactly(URL+2);
		assertThat(parent.get().getMetadata().getPlugins().get("plugin/comment")).isEqualTo(1);
	}

}
