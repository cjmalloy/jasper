package jasper.repository;

import jasper.DisabledOnSqlite;
import jasper.IntegrationTest;
import jasper.repository.spec.SortSpec.TagValueSort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class IndexRepositoryIT {

	@Autowired
	IndexRepository indexRepository;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void testBuildAndDropTags() {
		indexRepository.dropTags();
		indexRepository.buildTags();
		indexRepository.dropTags();
	}

	@Test
	void testBuildAndDropSources() {
		indexRepository.dropSources();
		indexRepository.buildSources();
		indexRepository.dropSources();
	}

	@Test
	void testBuildAndDropPublished() {
		indexRepository.dropPublished();
		indexRepository.buildPublished();
		indexRepository.dropPublished();
	}

	@Test
	void testBuildAndDropModified() {
		indexRepository.dropModified();
		indexRepository.buildModified();
		indexRepository.dropModified();
	}

	@Test
	void testUpdateHotTags() {
		indexRepository.updateHotTags(List.of("plugin/progress:num", "tags->plugin/duration:dur", "plugin/title", "invalid'tag"));
		indexRepository.updateHotTags(List.of());
	}

	List<String> hotTagIndexes() {
		return jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'ref' AND indexname LIKE 'ref\\_hot\\_%' ORDER BY indexname", String.class);
	}

	@Test
	@DisabledOnSqlite
	void testBuildAndDropHotTags() {
		var progress = IndexRepositoryImplPostgres.hotTagIndexName(TagValueSort.parse("plugin/progress:num"));
		var duration = IndexRepositoryImplPostgres.hotTagIndexName(TagValueSort.parse("plugin/duration:dur"));
		var progressText = IndexRepositoryImplPostgres.hotTagIndexName(TagValueSort.parse("plugin/progress"));
		assertThat(progress).startsWith("ref_hot_plugin_progress_value_num_");
		assertThat(progressText).isNotEqualTo(progress);
		assertThat(IndexRepositoryImplPostgres.hotTagIndexName(TagValueSort.parse("a".repeat(200) + ":dur")).length()).isLessThanOrEqualTo(63);

		indexRepository.updateHotTags(List.of("plugin/progress:num", "tags->plugin/duration:dur", "invalid'tag"));
		assertThat(hotTagIndexes()).containsExactlyInAnyOrder(progress, duration);

		indexRepository.updateHotTags(List.of("plugin/progress:num", "plugin/progress"));
		assertThat(hotTagIndexes()).containsExactlyInAnyOrder(progress, progressText);

		indexRepository.updateHotTags(List.of());
		assertThat(hotTagIndexes()).isEmpty();
	}
}
