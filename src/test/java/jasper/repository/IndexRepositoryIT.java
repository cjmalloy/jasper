package jasper.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jasper.DisabledOnSqlite;
import jasper.IntegrationTest;
import jasper.SqlCapture;
import jasper.repository.filter.RefFilter;
import jasper.repository.spec.RefSpec;
import jasper.repository.spec.SortSpec.TagValueSort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.springframework.transaction.annotation.Transactional;

import jasper.domain.Metadata;
import jasper.domain.Ref;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class IndexRepositoryIT {

	@Autowired
	IndexRepository indexRepository;

	@Autowired
	RefRepository refRepository;

	@Autowired
	JdbcTemplate jdbc;

	@PersistenceContext
	EntityManager em;

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
	void testBuildAndDropIgnored() {
		indexRepository.dropIgnored();
		indexRepository.buildIgnored();
		indexRepository.dropIgnored();
	}

	@Test
	void testBuildAndDropCascade() {
		indexRepository.dropCascade();
		indexRepository.buildCascade();
		indexRepository.dropCascade();
	}

	@Test
	void testBuildAndDropRegen() {
		indexRepository.dropRegen();
		indexRepository.buildRegen();
		indexRepository.dropRegen();
	}

	@Test
	void testBuildTwice() {
		indexRepository.buildModified();
		indexRepository.buildModified();
		indexRepository.buildIgnored();
		indexRepository.buildIgnored();
		indexRepository.buildCascade();
		indexRepository.buildCascade();
		indexRepository.buildRegen();
		indexRepository.buildRegen();
	}

	@Test
	void testBuildAndDropNewResponse() {
		indexRepository.dropNewResponse();
		indexRepository.buildNewResponse();
		indexRepository.buildNewResponse();
		indexRepository.dropNewResponse();
	}

	@Test
	void testBuildAndDropNewReaction() {
		indexRepository.dropNewReaction();
		indexRepository.buildNewReaction();
		indexRepository.buildNewReaction();
		indexRepository.dropNewReaction();
	}

	/**
	 * Runs a sort through {@link RefSpec#sort} and explains the SQL Hibernate rendered for it,
	 * so the test fails if Hibernate or the dialect stops rendering the indexed expression.
	 */
	String explainSort(String column, Sort.Direction direction, boolean sqlite) {
		var sql = SqlCapture.capture(() -> refRepository.findAll(
				RefSpec.sort(RefFilter.builder().build().spec(), PageRequest.of(0, 20, Sort.by(direction, column))),
				PageRequest.ofSize(20)))
			.stream()
			.filter(s -> s.contains(" order by "))
			.findFirst()
			.orElseThrow();
		if (!sqlite) em.createNativeQuery("SET LOCAL enable_seqscan = off").executeUpdate();
		return ((List<?>) em.createNativeQuery((sqlite ? "EXPLAIN QUERY PLAN " : "EXPLAIN ") + sql)
				.setParameter(1, sqlite ? 20 : 0)
				.setParameter(2, sqlite ? 0 : 20)
				.getResultList()).stream()
			.map(row -> row instanceof Object[] cols ? cols[cols.length - 1].toString() : row.toString())
			.collect(Collectors.joining("\n"));
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testNewResponseAndReactionIndexUsedForSortPostgres() {
		indexRepository.buildNewResponse();
		indexRepository.buildNewReaction();
		for (var direction : Sort.Direction.values()) {
			assertThat(explainSort("metadata->newResponse", direction, false)).as("newResponse %s", direction).contains("ref_new_response_index");
			assertThat(explainSort("metadata->newReaction", direction, false)).as("newReaction %s", direction).contains("ref_new_reaction_index");
		}
	}

	@Test
	@EnabledIf(expression = "#{environment.acceptsProfiles(T(org.springframework.core.env.Profiles).of('sqlite'))}", loadContext = true)
	@Transactional
	void testNewResponseAndReactionIndexUsedForSortSqlite() {
		indexRepository.buildNewResponse();
		indexRepository.buildNewReaction();
		for (var direction : Sort.Direction.values()) {
			assertThat(explainSort("metadata->newResponse", direction, true)).as("newResponse %s", direction).contains("ref_new_response_index");
			assertThat(explainSort("metadata->newReaction", direction, true)).as("newReaction %s", direction).contains("ref_new_reaction_index");
		}
	}

	String explainPostgres(String sql) {
		em.createNativeQuery("SET LOCAL enable_seqscan = off").executeUpdate();
		return ((List<?>) em.createNativeQuery("EXPLAIN " + sql).getResultList()).stream()
			.map(Object::toString)
			.collect(Collectors.joining("\n"));
	}

	// SQL generated by Hibernate for RefRepository.clearIgnored, getRefCascade and getRefBackfill

	@Test
	@DisabledOnSqlite
	@Transactional
	void testIgnoredIndexUsedPostgres() {
		indexRepository.buildIgnored();
		assertThat(explainPostgres("""
			update ref r1_0 set metadata=jsonb_set(r1_0.metadata, '{ignored}', 'false'::jsonb, true)
			where r1_0.origin='@test' and r1_0.modified<=now() and ( r1_0.metadata )->>( 'ignored' )='true'"""))
			.contains("ref_ignored_index");
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

	@Test
	@DisabledOnSqlite
	@Transactional
	void testCascadeIndexUsedPostgres() {
		indexRepository.buildCascade();
		assertThat(explainPostgres("""
			select r1_0.url from ref r1_0
			where ( r1_0.metadata )->>( 'cascade' )='true' and ( '@test'='' or r1_0.origin='@test' or r1_0.origin like ('@test'||'.%') escape '' )
			order by r1_0.modified desc fetch first 1 rows only"""))
			.contains("ref_cascade_index");
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testRegenIndexUsedPostgres() {
		indexRepository.buildRegen();
		assertThat(explainPostgres("""
			select r1_0.url from ref r1_0
			where ( r1_0.metadata is null or ( r1_0.metadata )->>( 'modified' ) is null or ( r1_0.metadata )->>( 'regen' )='true' )
				and ( '@test'='' or r1_0.origin='@test' or r1_0.origin like ('@test'||'.%') escape '' )
			order by r1_0.modified desc fetch first 1 rows only"""))
			.contains("ref_regen_index");
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testHotTagIndexUsedForTagValueFilterPostgres() {
		indexRepository.updateHotTags(List.of("plugin/progress:num"));
		assertThat(explainPostgres("""
			select r1_0.url from ref r1_0
			where tag_value_num(r1_0.tags, 'plugin/progress')<(37)::numeric"""))
			.contains(IndexRepositoryImplPostgres.hotTagIndexName(TagValueSort.parse("plugin/progress:num")));
	}

	// SQL rendered by the PostgreSQL dialect for safe_numeric and safe_jsonb_array_length

	static String safeNumeric(String expr) {
		return "(CASE WHEN jsonb_typeof(" + expr + ") = 'number' THEN (" + expr + ")::numeric END)";
	}

	static String safeLength(String expr) {
		return "(CASE WHEN jsonb_typeof(" + expr + ") = 'array' THEN jsonb_array_length(" + expr + ") END)";
	}

	List<String> hotPluginIndexes() {
		return jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'ref' AND (indexname LIKE 'ref\\_hotp\\_%' OR indexname LIKE 'ref\\_hotps\\_%') ORDER BY indexname", String.class);
	}

	@Test
	void testUpdateHotPlugins() {
		indexRepository.updateHotPlugins(List.of("plugin/test->size:num", "plugins->plugin/test->name", "plugin/test->items[0]->tags:len", "invalid'plugin"));
		indexRepository.updateHotPlugins(List.of());
	}

	@Test
	void testHotPluginExpression() {
		assertThat(IndexRepositoryImplPostgres.hotPluginColumn("plugin/test->name")).isEqualTo("plugins->plugin/test->name");
		assertThat(IndexRepositoryImplPostgres.hotPluginColumn("plugins->plugin/test->name")).isEqualTo("plugins->plugin/test->name");
		assertThat(IndexRepositoryImplPostgres.hotPluginColumn("plugin/test->na'me")).isNull();
		assertThat(IndexRepositoryImplPostgres.hotPluginColumn("plugin/test->name:dur")).isNull();
		assertThat(IndexRepositoryImplPostgres.hotPluginColumn("+plugin/test")).isNotNull();
		assertThat(IndexRepositoryImplPostgres.hotPluginExpression("plugins->plugin/test->name"))
			.isEqualTo("((plugins)->('plugin/test'))->>('name')");
		assertThat(IndexRepositoryImplPostgres.hotPluginExpression("plugins->plugin/score:num"))
			.isEqualTo(safeNumeric("(plugins)->('plugin/score')"));
		assertThat(IndexRepositoryImplPostgres.hotPluginExpression("plugins->plugin/test->tags:len"))
			.isEqualTo(safeLength("((plugins)->('plugin/test'))->('tags')"));
		assertThat(IndexRepositoryImplPostgres.hotPluginExpression("plugins->plugin/test->items[0]->score:num"))
			.isEqualTo(safeNumeric("(jsonb_array_element(((plugins)->('plugin/test'))->('items'), 0))->('score')"));
		assertThat(IndexRepositoryImplPostgres.hotPluginExpression("plugins->plugin/test->items[1]"))
			.isEqualTo("jsonb_array_element_text(((plugins)->('plugin/test'))->('items'), 1)");
		assertThat(IndexRepositoryImplPostgres.hotPluginExpression("plugins->plugin/test->items[1]:len"))
			.isEqualTo(safeLength("jsonb_array_element(((plugins)->('plugin/test'))->('items'), 1)"));
		assertThat(IndexRepositoryImplPostgres.hotPluginIndexName("plugins->plugin/" + "a".repeat(200)).length()).isLessThanOrEqualTo(63);
	}

	@Test
	@DisabledOnSqlite
	void testHotPluginDropsIndexesWithoutNullsFirst() {
		var size = IndexRepositoryImplPostgres.hotPluginIndexName("plugins->plugin/test->size:num");
		var legacySort = "ref_hotps_plugin_test_size_num_legacy";
		indexRepository.updateHotPlugins(List.of());
		jdbc.execute("CREATE INDEX " + size + " ON ref ((" + IndexRepositoryImplPostgres.hotPluginExpression("plugins->plugin/test->size:num") + "))");
		jdbc.execute("CREATE INDEX " + legacySort + " ON ref ((COALESCE(" + IndexRepositoryImplPostgres.hotPluginExpression("plugins->plugin/test->size:num") + ", 0.0)))");

		indexRepository.updateHotPlugins(List.of("plugin/test->size:num"));
		assertThat(hotPluginIndexes()).containsExactly(size);
		assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = ?", String.class, size)).contains("NULLS FIRST");

		indexRepository.updateHotPlugins(List.of());
		assertThat(hotPluginIndexes()).isEmpty();
	}

	@Test
	@DisabledOnSqlite
	void testMetadataDateIndexDropsCoalesceIndexPostgres() {
		indexRepository.dropNewResponse();
		jdbc.execute("CREATE INDEX ref_new_response_index ON ref ((COALESCE((metadata)->>('newResponse'), '')))");
		indexRepository.buildNewResponse();
		assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'ref_new_response_index'", String.class))
			.contains("NULLS FIRST")
			.doesNotContainIgnoringCase("coalesce");
	}

	@Test
	@DisabledOnSqlite
	void testBuildAndDropHotPlugins() {
		var size = IndexRepositoryImplPostgres.hotPluginIndexName("plugins->plugin/test->size:num");
		var name = IndexRepositoryImplPostgres.hotPluginIndexName("plugins->plugin/test->name");
		var sizeText = IndexRepositoryImplPostgres.hotPluginIndexName("plugins->plugin/test->size");
		assertThat(size).startsWith("ref_hotp_plugin_test_size_num_");
		assertThat(sizeText).isNotEqualTo(size);

		indexRepository.updateHotTags(List.of("plugin/progress:num"));
		indexRepository.updateHotPlugins(List.of("plugin/test->size:num", "plugins->plugin/test->name", "invalid'plugin"));
		assertThat(hotPluginIndexes()).containsExactlyInAnyOrder(size, name);

		indexRepository.updateHotPlugins(List.of("plugin/test->size:num", "plugin/test->size"));
		assertThat(hotPluginIndexes()).containsExactlyInAnyOrder(size, sizeText);
		assertThat(hotTagIndexes()).hasSize(1);

		indexRepository.updateHotPlugins(List.of());
		assertThat(hotPluginIndexes()).isEmpty();
		assertThat(hotTagIndexes()).hasSize(1);
		indexRepository.updateHotTags(List.of());
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testHotPluginIndexWriteNonMatchingTypesPostgres() {
		indexRepository.updateHotPlugins(List.of("plugin/test->size:num", "plugin/test->items:len", "plugin/test->items[0]->score:num"));
		em.createNativeQuery("""
			INSERT INTO ref (url, origin, modified, published, created, plugins) VALUES
			('https://www.example.com/hotp1', '', now() + interval '1 ms', now(), now(), '{"plugin/test": {"size": "big", "items": "none"}}'::jsonb),
			('https://www.example.com/hotp2', '', now() + interval '2 ms', now(), now(), '{"plugin/test": {"size": 5, "items": [{"score": "high"}]}}'::jsonb),
			('https://www.example.com/hotp3', '', now() + interval '3 ms', now(), now(), '{"plugin/test": {"items": {"0": 1}}}'::jsonb)""").executeUpdate();
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testHotPluginIndexUsedForPluginFilterPostgres() {
		var filters = List.of("plugin/test->name/m", "plugin/test->size:num/20", "plugin/test->tags:len/2",
			"plugin/test->items[0]->score:num/20", "plugin/test->items[1]/m", "plugin/test->items[1]:len/2", "plugin/score:num/20");
		indexRepository.updateHotPlugins(filters.stream().map(f -> f.substring(0, f.lastIndexOf('/'))).toList());
		for (var filter : filters) {
			var column = filter.substring(0, filter.lastIndexOf('/'));
			var value = filter.substring(filter.lastIndexOf('/') + 1);
			var index = IndexRepositoryImplPostgres.hotPluginIndexName("plugins->" + column);
			// Numbers and lengths are inlined, text is bound
			var params = column.endsWith(":num") || column.endsWith(":len") ? new Object[0] : new Object[]{ value };
			assertThat(explainFilter(RefFilter.builder().filterBefore(List.of("plugins->" + filter)).build().spec(), false, params))
				.as("%s before", filter)
				.contains(index);
			assertThat(explainFilter(RefFilter.builder().filterAfter(List.of("plugins->" + filter)).build().spec(), false, params))
				.as("%s after", filter)
				.contains(index);
		}
	}

	/**
	 * Runs a plugin sort through {@link RefSpec#sort} and explains the SQL Hibernate rendered for it,
	 * so the test fails if Hibernate or the dialect stops rendering the indexed expression.
	 */
	String explainPluginSortPostgres(String column, Sort.Direction direction) {
		var sql = SqlCapture.capture(() -> refRepository.findAll(
				RefSpec.sort(RefFilter.builder().build().spec(), PageRequest.of(0, 20, Sort.by(direction, column))),
				PageRequest.ofSize(20)))
			.stream()
			.filter(s -> s.contains(" order by "))
			.findFirst()
			.orElseThrow();
		em.createNativeQuery("SET LOCAL enable_seqscan = off").executeUpdate();
		// Bind the page offset and limit
		return ((List<?>) em.createNativeQuery("EXPLAIN " + sql)
				.setParameter(1, 0)
				.setParameter(2, 20)
				.getResultList()).stream()
			.map(Object::toString)
			.collect(Collectors.joining("\n"));
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testHotPluginIndexUsedForPluginSortPostgres() {
		var columns = List.of("plugin/test->name", "plugin/test->size:num", "plugin/test->tags:len",
			"plugin/test->items[0]->score:num", "plugin/test->items[1]", "plugin/test->items[1]:len", "plugin/score:num");
		indexRepository.updateHotPlugins(columns);
		for (var column : columns) {
			for (var direction : Sort.Direction.values()) {
				assertThat(SqlCapture.capture(() -> refRepository.findAll(
						RefSpec.sort(RefFilter.builder().build().spec(), PageRequest.of(0, 20, Sort.by(direction, "plugins->" + column))),
						PageRequest.ofSize(20))).getLast())
					.as("%s %s", column, direction)
					.doesNotContainIgnoringCase("coalesce(")
					.contains(direction.isAscending() ? " asc nulls first" : " desc nulls last");
				assertThat(explainPluginSortPostgres("plugins->" + column, direction))
					.as("%s %s", column, direction)
					.contains(IndexRepositoryImplPostgres.hotPluginIndexName("plugins->" + column));
			}
		}
	}

	List<String> hotResponseIndexes() {
		return jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'ref' AND (indexname LIKE 'ref\\_hotr\\_%' OR indexname LIKE 'ref\\_hotrr\\_%') ORDER BY indexname", String.class);
	}

	@Test
	void testUpdateHotResponses() {
		indexRepository.updateHotResponses(List.of("plugin/comment", "+plugin/log", "invalid'plugin", "notaplugin"));
		indexRepository.updateHotRemoteResponses(List.of("plugin/comment", "invalid'plugin"));
		indexRepository.updateHotResponses(List.of());
		indexRepository.updateHotRemoteResponses(null);
	}

	@Test
	void testHotResponseExpression() {
		assertThat(IndexRepositoryImplPostgres.hotResponseExpression("plugins", "plugin/comment"))
			.isEqualTo("COALESCE(" + safeNumeric("((metadata)->('plugins'))->('plugin/comment')") + ", 0.0)");
		assertThat(IndexRepositoryImplPostgres.hotResponseExpression("remotePlugins", "plugin/comment"))
			.isEqualTo("COALESCE(" + safeNumeric("((metadata)->('remotePlugins'))->('plugin/comment')") + ", 0.0)");
		assertThat(IndexRepositoryImplPostgres.hotResponseIndexName("plugins", "plugin/comment"))
			.startsWith("ref_hotr_plugin_comment_");
		assertThat(IndexRepositoryImplPostgres.hotResponseIndexName("remotePlugins", "plugin/comment"))
			.startsWith("ref_hotrr_plugin_comment_");
		assertThat(IndexRepositoryImplPostgres.hotResponseIndexName("plugins", "plugin/" + "a".repeat(200)).length()).isLessThanOrEqualTo(63);
		assertThat(IndexRepositoryImplPostgres.hotResponseIndexName("remotePlugins", "plugin/" + "a".repeat(200)).length()).isLessThanOrEqualTo(63);
	}

	@Test
	@DisabledOnSqlite
	void testBuildAndDropHotResponses() {
		var comment = IndexRepositoryImplPostgres.hotResponseIndexName("plugins", "plugin/comment");
		var log = IndexRepositoryImplPostgres.hotResponseIndexName("plugins", "+plugin/log");
		var remoteComment = IndexRepositoryImplPostgres.hotResponseIndexName("remotePlugins", "plugin/comment");

		indexRepository.updateHotTags(List.of("plugin/progress:num"));
		indexRepository.updateHotPlugins(List.of("plugin/test->size:num"));
		indexRepository.updateHotResponses(List.of("plugin/comment", "+plugin/log", "invalid'plugin"));
		indexRepository.updateHotRemoteResponses(List.of("plugin/comment"));
		assertThat(hotResponseIndexes()).containsExactlyInAnyOrder(comment, log, remoteComment);

		indexRepository.updateHotResponses(List.of("+plugin/log"));
		assertThat(hotResponseIndexes()).containsExactlyInAnyOrder(log, remoteComment);

		indexRepository.updateHotRemoteResponses(List.of());
		assertThat(hotResponseIndexes()).containsExactlyInAnyOrder(log);

		indexRepository.updateHotResponses(List.of());
		assertThat(hotResponseIndexes()).isEmpty();
		assertThat(hotTagIndexes()).hasSize(1);
		assertThat(hotPluginIndexes()).hasSize(1);
		indexRepository.updateHotTags(List.of());
		indexRepository.updateHotPlugins(List.of());
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testHotResponseIndexUsedForSortPostgres() {
		indexRepository.updateHotResponses(List.of("plugin/comment"));
		indexRepository.updateHotRemoteResponses(List.of("plugin/comment"));
		for (var direction : Sort.Direction.values()) {
			assertThat(explainPluginSortPostgres("metadata->plugins->plugin/comment", direction))
				.as("plugins %s", direction)
				.contains(IndexRepositoryImplPostgres.hotResponseIndexName("plugins", "plugin/comment"));
			assertThat(explainPluginSortPostgres("metadata->remotePlugins->plugin/comment", direction))
				.as("remotePlugins %s", direction)
				.contains(IndexRepositoryImplPostgres.hotResponseIndexName("remotePlugins", "plugin/comment"));
		}
	}

	/**
	 * Runs a filter through {@link RefFilter#spec} and explains the SQL Hibernate rendered for it,
	 * so the test fails if Hibernate or the dialect stops rendering the indexed expression.
	 */
	String explainFilterPostgres(RefFilter filter) {
		var sql = SqlCapture.capture(() -> refRepository.findAll(filter.spec()))
			.stream()
			.filter(s -> s.contains(" from ref "))
			.findFirst()
			.orElseThrow();
		em.createNativeQuery("SET LOCAL enable_seqscan = off").executeUpdate();
		return ((List<?>) em.createNativeQuery("EXPLAIN " + sql).getResultList()).stream()
			.map(Object::toString)
			.collect(Collectors.joining("\n"));
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testHotResponseIndexUsedForFilterPostgres() {
		indexRepository.updateHotResponses(List.of("plugin/comment"));
		indexRepository.updateHotRemoteResponses(List.of("plugin/comment"));
		assertThat(explainFilterPostgres(RefFilter.builder().filterAfter(List.of("metadata->plugins->plugin/comment/0")).build()))
			.contains(IndexRepositoryImplPostgres.hotResponseIndexName("plugins", "plugin/comment"));
		assertThat(explainFilterPostgres(RefFilter.builder().filterEquals(List.of("metadata->plugins->plugin/comment/0")).build()))
			.contains(IndexRepositoryImplPostgres.hotResponseIndexName("plugins", "plugin/comment"));
		assertThat(explainFilterPostgres(RefFilter.builder().filterAfter(List.of("metadata->remotePlugins->plugin/comment/0")).build()))
			.contains(IndexRepositoryImplPostgres.hotResponseIndexName("remotePlugins", "plugin/comment"));
	}

	String explainSqlite(String sql) {
		return ((List<?>) em.createNativeQuery("EXPLAIN QUERY PLAN " + sql).getResultList()).stream()
			.map(row -> row instanceof Object[] cols ? cols[cols.length - 1].toString() : row.toString())
			.collect(Collectors.joining("\n"));
	}

	static String field(String metadata, String key) {
		var path = "'$.\"' || REPLACE('" + key + "', '\"', '\"\"') || '\"'";
		return "( CASE WHEN json_type(" + metadata + ", " + path + ") IN ('true', 'false') THEN json_type(" + metadata + ", " + path + ") ELSE CAST(json_extract(" + metadata + ", " + path + ") AS TEXT) END )";
	}

	@Test
	@EnabledIf(expression = "#{environment.acceptsProfiles(T(org.springframework.core.env.Profiles).of('sqlite'))}", loadContext = true)
	@Transactional
	void testIgnoredIndexUsedSqlite() {
		indexRepository.buildIgnored();
		assertThat(explainSqlite("""
			update ref set metadata=json_set(metadata, '$.' || REPLACE(REPLACE('{ignored}', '{', ''), '}', ''), CASE WHEN 1 IS NOT NULL THEN json('false') ELSE json('false') END)
			where origin='@test' and modified<='2020-01-01T00:00:00Z' and %s='true'""".formatted(field("metadata", "ignored"))))
			.contains("ref_ignored_index");
	}

	@Test
	@EnabledIf(expression = "#{environment.acceptsProfiles(T(org.springframework.core.env.Profiles).of('sqlite'))}", loadContext = true)
	@Transactional
	void testCascadeIndexUsedSqlite() {
		indexRepository.buildCascade();
		assertThat(explainSqlite("""
			select r1_0.url from ref r1_0
			where %s='true' and ( '@test'='' or r1_0.origin='@test' or r1_0.origin like ('@test'||'.%%') )
			order by r1_0.modified desc limit 1""".formatted(field("r1_0.metadata", "cascade"))))
			.contains("ref_cascade_index");
	}

	@Test
	@EnabledIf(expression = "#{environment.acceptsProfiles(T(org.springframework.core.env.Profiles).of('sqlite'))}", loadContext = true)
	@Transactional
	void testRegenIndexUsedSqlite() {
		indexRepository.buildRegen();
		assertThat(explainSqlite("""
			select r1_0.url from ref r1_0
			where ( r1_0.metadata is null or %s is null or %s='true' )
				and ( '@test'='' or r1_0.origin='@test' or r1_0.origin like ('@test'||'.%%') )
			order by r1_0.modified desc limit 1""".formatted(field("r1_0.metadata", "modified"), field("r1_0.metadata", "regen"))))
			.contains("ref_regen_index");
	}

	static final String SQLITE = "#{environment.acceptsProfiles(T(org.springframework.core.env.Profiles).of('sqlite'))}";

	/**
	 * Runs a filter through {@link RefFilter#spec} and explains the SQL Hibernate rendered for it.
	 */
	String explainFilter(Specification<Ref> spec, boolean sqlite, Object... params) {
		var sql = SqlCapture.capture(() -> refRepository.findAll(spec))
			.stream()
			.filter(s -> s.contains(" from ref "))
			.findFirst()
			.orElseThrow();
		if (!sqlite) em.createNativeQuery("SET LOCAL enable_seqscan = off").executeUpdate();
		return jdbc.queryForList((sqlite ? "EXPLAIN QUERY PLAN " : "EXPLAIN ") + sql, params).stream()
			.map(row -> row.values().stream().reduce((a, b) -> b).orElseThrow().toString())
			.collect(Collectors.joining("\n"));
	}

	@Test
	void testBuildAndDropCreated() {
		indexRepository.dropCreated();
		indexRepository.buildCreated();
		indexRepository.buildCreated();
		indexRepository.dropCreated();
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testCreatedIndexUsedPostgres() {
		indexRepository.buildCreated();
		for (var direction : Sort.Direction.values()) {
			assertThat(explainSort("created", direction, false)).as("created %s", direction).contains("ref_created_index");
		}
		var now = Instant.parse("2026-01-01T00:00:00Z");
		var param = Timestamp.from(now);
		assertThat(explainFilter(RefFilter.builder().filterBefore(List.of("created/" + now)).build().spec(), false, param)).contains("ref_created_index");
		assertThat(explainFilter(RefFilter.builder().filterAfter(List.of("created/" + now)).build().spec(), false, param)).contains("ref_created_index");
	}

	@Test
	@EnabledIf(expression = SQLITE, loadContext = true)
	@Transactional
	void testCreatedIndexUsedSqlite() {
		indexRepository.buildCreated();
		for (var direction : Sort.Direction.values()) {
			assertThat(explainSort("created", direction, true)).as("created %s", direction).contains("ref_created_index");
		}
		var now = Instant.parse("2026-01-01T00:00:00Z");
		var param = Timestamp.from(now);
		assertThat(explainFilter(RefFilter.builder().filterBefore(List.of("created/" + now)).build().spec(), true, param)).contains("ref_created_index");
		assertThat(explainFilter(RefFilter.builder().filterAfter(List.of("created/" + now)).build().spec(), true, param)).contains("ref_created_index");
	}

	@Test
	void testBuildAndDropTitle() {
		indexRepository.dropTitle();
		indexRepository.buildTitle();
		indexRepository.buildTitle();
		indexRepository.dropTitle();
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testTitleIndexUsedPostgres() {
		indexRepository.buildTitle();
		for (var direction : Sort.Direction.values()) {
			assertThat(explainSort("title", direction, false)).as("title %s", direction).contains("ref_title_index");
		}
	}

	@Test
	@EnabledIf(expression = SQLITE, loadContext = true)
	@Transactional
	void testTitleIndexUsedSqlite() {
		indexRepository.buildTitle();
		for (var direction : Sort.Direction.values()) {
			assertThat(explainSort("title", direction, true)).as("title %s", direction).contains("ref_title_index");
		}
	}

	/**
	 * Origin filters and sorts must use the origin indexes.
	 */
	void assertOriginIndexesUsed(boolean sqlite) {
		indexRepository.buildOrigin();
		for (var direction : Sort.Direction.values()) {
			assertThat(explainSort("origin", direction, sqlite)).as("origin %s", direction).contains("ref_origin_index");
			assertThat(explainSort("origin:len", direction, sqlite)).as("origin:len %s", direction).contains("ref_origin_nesting_index");
		}
		assertThat(explainFilter(RefFilter.builder().origin("@test").build().spec(), sqlite, "@test")).containsPattern("ref_origin_(prefix_)?index");
		assertThat(explainFilter(RefFilter.builder().nesting(2).build().spec(), sqlite, 2)).contains("ref_origin_nesting_index");
	}

	@Test
	void testBuildAndDropOrigin() {
		indexRepository.dropOrigin();
		indexRepository.buildOrigin();
		indexRepository.buildOrigin();
		indexRepository.dropOrigin();
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testOriginIndexesUsedPostgres() {
		assertOriginIndexesUsed(false);
		assertThat(explainFilter(RefFilter.builder().origin("@test.*").build().spec(), false, "@test", "@test.%")).contains("ref_origin_prefix_index");
	}

	@Test
	@EnabledIf(expression = SQLITE, loadContext = true)
	@Transactional
	void testOriginIndexesUsedSqlite() {
		assertOriginIndexesUsed(true);
	}

	/**
	 * Each metadata date filter must use the same index as the matching sort.
	 */
	void assertMetadataDateIndexesUsed(boolean sqlite) {
		indexRepository.buildNewResponse();
		indexRepository.buildNewReaction();
		var now = Instant.parse("2026-01-01T00:00:00.5Z");
		var param = Metadata.timestamp(now);
		assertThat(explainFilter(RefFilter.builder().filterBefore(List.of("metadata->newResponse/" + now)).build().spec(), sqlite, param)).contains("ref_new_response_index");
		assertThat(explainFilter(RefFilter.builder().filterAfter(List.of("metadata->newResponse/" + now)).build().spec(), sqlite, param)).contains("ref_new_response_index");
		assertThat(explainFilter(RefFilter.builder().filterBefore(List.of("metadata->newReaction/" + now)).build().spec(), sqlite, param)).contains("ref_new_reaction_index");
		assertThat(explainFilter(RefFilter.builder().filterAfter(List.of("metadata->newReaction/" + now)).build().spec(), sqlite, param)).contains("ref_new_reaction_index");
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testMetadataDateIndexesUsedForFilterPostgres() {
		assertMetadataDateIndexesUsed(false);
	}

	@Test
	@EnabledIf(expression = SQLITE, loadContext = true)
	@Transactional
	void testMetadataDateIndexesUsedForFilterSqlite() {
		assertMetadataDateIndexesUsed(true);
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testHotResponseIndexUsedForCountFilterPostgres() {
		indexRepository.updateHotResponses(List.of("plugin/comment"));
		indexRepository.updateHotRemoteResponses(List.of("plugin/user/read"));
		assertThat(explainFilter(RefFilter.builder().filterAfter(List.of("metadata->plugins->plugin/comment/10")).build().spec(), false))
			.contains(IndexRepositoryImplPostgres.hotResponseIndexName("plugins", "plugin/comment"));
		assertThat(explainFilter(RefFilter.builder().filterBefore(List.of("metadata->plugins->plugin/comment:num/3")).build().spec(), false))
			.contains(IndexRepositoryImplPostgres.hotResponseIndexName("plugins", "plugin/comment"));
		assertThat(explainFilter(RefFilter.builder().filterAfter(List.of("metadata->remotePlugins->plugin/user/read/10")).build().spec(), false))
			.contains(IndexRepositoryImplPostgres.hotResponseIndexName("remotePlugins", "plugin/user/read"));
	}

	static final List<List<String>> LENGTH_INDEXES = List.of(
		List.of("tags:len", "ref_tags_len_index"),
		List.of("metadata->expandedTags", "ref_expanded_tags_len_index"),
		List.of("sources:len", "ref_sources_len_index"),
		List.of("alternateUrls:len", "ref_alternate_urls_len_index"),
		List.of("metadata->responses", "ref_responses_len_index"),
		List.of("metadata->internalResponses:len", "ref_internal_responses_len_index"));

	/**
	 * Each length sort and filter must use the length index built with the matching index config.
	 */
	void assertLengthIndexesUsed(boolean sqlite) {
		indexRepository.buildTags();
		indexRepository.buildExpandedTags();
		indexRepository.buildSources();
		indexRepository.buildAlts();
		indexRepository.buildResponses();
		indexRepository.buildInternalResponses();
		for (var e : LENGTH_INDEXES) {
			for (var direction : Sort.Direction.values()) {
				assertThat(explainSort(e.get(0), direction, sqlite)).as("%s %s", e.get(0), direction).contains(e.get(1));
			}
			assertThat(explainFilter(RefFilter.builder().filterAfter(List.of(e.get(0) + "/3")).build().spec(), sqlite)).as("%s filter", e.get(0)).contains(e.get(1));
		}
	}

	@Test
	@DisabledOnSqlite
	@Transactional
	void testLengthIndexesUsedPostgres() {
		assertLengthIndexesUsed(false);
	}

	@Test
	@EnabledIf(expression = SQLITE, loadContext = true)
	@Transactional
	void testLengthIndexesUsedSqlite() {
		assertLengthIndexesUsed(true);
	}

	@Test
	void testBuildAndDropLengthIndexes() {
		indexRepository.buildAlts();
		indexRepository.buildResponses();
		indexRepository.buildInternalResponses();
		indexRepository.buildAlts();
		indexRepository.buildResponses();
		indexRepository.buildInternalResponses();
		indexRepository.dropAlts();
		indexRepository.dropResponses();
		indexRepository.dropInternalResponses();
	}
}
