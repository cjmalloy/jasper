package jasper.repository;

import jasper.IntegrationTest;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class RefRepositoryIT {

	@Autowired
	RefRepository refRepository;

	@BeforeEach
	void init() {
		refRepository.deleteAll();
	}

	@Test
	void testDropMetadata_MarksRefWithoutRegen() {
		var ref = new Ref();
		ref.setUrl("https://www.example.com/");
		ref.setOrigin("");
		ref.setMetadata(Metadata.builder().build());
		refRepository.save(ref);

		refRepository.dropMetadata("");

		var updated = refRepository.findOneByUrlAndOrigin("https://www.example.com/", "").orElseThrow();
		assertThat(updated.getMetadata()).isNotNull();
		assertThat(updated.getMetadata().isRegen()).isTrue();
	}

	@Test
	void testCountPluginTagsInResponses_ReturnsPluginTagsWithCounts() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var resp1 = new Ref();
		resp1.setUrl("http://example.com/resp1");
		resp1.setOrigin("");
		resp1.setSources(List.of("http://example.com/parent"));
		resp1.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "+plugin/vote/up", "public"))
			.build());
		refRepository.save(resp1);

		var resp2 = new Ref();
		resp2.setUrl("http://example.com/resp2");
		resp2.setOrigin("");
		resp2.setSources(List.of("http://example.com/parent"));
		resp2.setMetadata(Metadata.builder()
			.expandedTags(List.of("plugin/comment", "public"))
			.build());
		refRepository.save(resp2);

		var map = refRepository.countPluginTagsInResponses("http://example.com/parent", "").stream()
			.collect(toMap(r -> (String) r[0], r -> ((Number) r[1]).longValue()));
		assertThat(map).containsEntry("plugin/comment", 2L);
		assertThat(map).containsEntry("+plugin/vote/up", 1L);
		assertThat(map).doesNotContainKey("public");
	}

	@Test
	void testCountPluginTagsInResponses_FallsBackToTagsAndSkipsObsolete() {
		var parent = new Ref();
		parent.setUrl("http://example.com/parent");
		parent.setOrigin("");
		refRepository.save(parent);

		var response = new Ref();
		response.setUrl("http://example.com/response");
		response.setOrigin("");
		response.setSources(List.of("http://example.com/parent"));
		response.setTags(List.of("plugin/comment", "public"));
		response.setMetadata(Metadata.builder().build());
		refRepository.save(response);

		var obsolete = new Ref();
		obsolete.setUrl("http://example.com/obsolete");
		obsolete.setOrigin("");
		obsolete.setSources(List.of("http://example.com/parent"));
		obsolete.setTags(List.of("plugin/comment", "plugin/thread"));
		obsolete.setMetadata(Metadata.builder().obsolete(true).build());
		refRepository.save(obsolete);

		var map = refRepository.countPluginTagsInResponses("http://example.com/parent", "").stream()
			.collect(toMap(r -> (String) r[0], r -> ((Number) r[1]).longValue()));
		assertThat(map).containsEntry("plugin/comment", 1L);
		assertThat(map).doesNotContainKeys("public", "plugin/thread");
	}
}
