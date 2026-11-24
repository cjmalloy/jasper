package jasper.repository.spec;

import jasper.repository.spec.SortSpec.Filter;
import jasper.repository.spec.SortSpec.Filter.Op;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SortSpecTest {

	void assertFilter(String filter, String column, Object value) {
		var f = Filter.parse(filter);
		assertThat(f).isNotNull();
		assertThat(f.column()).isEqualTo(column);
		assertThat(f.op()).isEqualTo(Op.AFTER);
		assertThat(f.value()).isEqualTo(value);
		assertThat(filter).matches(Filter.REGEX);
	}

	@Test
	void testFilterOp() {
		assertThat(Filter.parse("modified/before/2024-01-01T00:00:00Z").op()).isEqualTo(Op.BEFORE);
		assertThat(Filter.parse("modified/after/2024-01-01T00:00:00Z").op()).isEqualTo(Op.AFTER);
		assertThat(Filter.parse("modified/equals/2024-01-01T00:00:00Z").op()).isEqualTo(Op.EQUALS);
		var f = Filter.parse("tags->plugin/after/before/x");
		assertThat(f.column()).isEqualTo("tags->plugin/after");
		assertThat(f.op()).isEqualTo(Op.BEFORE);
		assertThat(f.value()).isEqualTo("x");
	}

	@Test
	void testDateFilter() {
		assertFilter("modified/after/2024-01-01T00:00:00Z", "modified", Instant.parse("2024-01-01T00:00:00Z"));
		assertFilter("published/after/2024-01-01T00:00:00.123Z", "published", Instant.parse("2024-01-01T00:00:00.123Z"));
		assertFilter("created/after/2024-01-01T00:00:00Z", "created", Instant.parse("2024-01-01T00:00:00Z"));
	}

	@Test
	void testMetadataDateFilter() {
		assertFilter("metadata->newResponse/after/2024-01-01T00:00:00Z", "metadata->newResponse", "2024-01-01T00:00:00.000000000Z");
		assertFilter("metadata->newReaction/after/2024-01-01T00:00:00.5Z", "metadata->newReaction", "2024-01-01T00:00:00.500000000Z");
	}

	@Test
	void testResponseCountFilter() {
		assertFilter("metadata->plugins->plugin/comment/after/0", "metadata->plugins->plugin/comment", new BigDecimal("0"));
		assertFilter("metadata->remotePlugins->plugin/user/read:num/after/10", "metadata->remotePlugins->plugin/user/read:num", new BigDecimal("10"));
		assertFilter("metadata->responses/after/3", "metadata->responses", 3);
	}

	@Test
	void testLengthFilter() {
		assertFilter("tags:len/after/3", "tags:len", 3);
		assertFilter("origin:len/after/1", "origin:len", 1);
		assertFilter("tag:len/after/2", "tag:len", 2);
	}

	@Test
	void testVoteFilter() {
		assertFilter("plugins->plugin/user/vote:top/after/5", "plugins->plugin/user/vote:top", new BigDecimal("5"));
	}

	@Test
	void testTagValueFilter() {
		assertFilter("tags->plugin/title/after/kiwi", "tags->plugin/title", "kiwi");
		assertFilter("tags->plugin/progress:num/after/2.5", "tags->plugin/progress:num", new BigDecimal("2.5"));
		assertFilter("tags->_plugin/duration:dur/after/pt1h", "tags->_plugin/duration:dur", BigDecimal.valueOf(3600.0));
		assertFilter("tags->plugin/duration:dur/after/PT1H", "tags->plugin/duration:dur", BigDecimal.valueOf(3600.0));
	}

	@Test
	void testPluginValueFilter() {
		assertFilter("plugins->plugin/test->style->color/after/blue", "plugins->plugin/test->style->color", "blue");
		assertFilter("plugins->plugin/test->url/after/https:", "plugins->plugin/test->url", "https:");
		assertFilter("plugins->_plugin/cache->contentLength:num/after/-3.5", "plugins->_plugin/cache->contentLength:num", new BigDecimal("-3.5"));
		assertFilter("plugins->plugin/test->ids:len/after/3", "plugins->plugin/test->ids:len", 3);
		assertFilter("plugins->plugin/a/b/after/c", "plugins->plugin/a/b", "c");
		assertFilter("plugins->plugin:len/after/3", "plugins->plugin:len", 3);
	}

	@Test
	void testConfigValueFilter() {
		assertFilter("config->pinned:len/after/0", "config->pinned:len", 0);
		assertFilter("external->ids[0]/after/abc", "external->ids[0]", "abc");
		assertFilter("schema->properties->name->type/after/string", "schema->properties->name->type", "string");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"modified/before/2024-01-01",
		"modified/before/yesterday",
		"metadata->newResponse/before/2024-13-01T00:00:00Z",
		"metadata->plugins->plugin/comment/before/abc",
		"metadata->responses/before/-1",
		"tags:len/before/x",
		"tags->plugin/progress:num/before/abc",
		"plugins->plugin/test->ids:len/before/-1",
		"plugins->plugin/test->size:num/before/abc",
	})
	void testInvalidFilterValue(String filter) {
		assertThat(Filter.parse(filter)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"plugin/test->size/equals/1",
		"title/equals/abc",
		"url/equals/abc",
		"metadata->modified/equals/2024-01-01T00:00:00Z",
		"metadata->userUrls/equals/abc",
		"tags->plugin",
		"tags->plugin/equals/",
		"tags->plugin/title/equals/a,b",
		"plugins->plugin/test->size",
		"plugins->plugin/test->size/equals/",
		"plugins->plugin/test->size/equals=1",
		"plugins->plugin/test->it's/equals/1",
		"plugins->plugin/test->name/equals/a,b",
		"plugins->custom->size/equals/1",
		"plugins->plugin/test->url/equals/https://a.com",
		"plugins->plugin/test:dur/equals/pt1h",
		"plugins->plugin",
		"plugins->plugin/equals/",
		"config/equals/1",
		"other->a/equals/1",
		"modified/2024-01-01T00:00:00Z",
		"modified/since/2024-01-01T00:00:00Z",
		"modified/AFTER/2024-01-01T00:00:00Z",
		"tags->plugin/title/after",
	})
	void testInvalidFilter(String filter) {
		assertThat(filter).doesNotMatch(Filter.REGEX);
		assertThat(Filter.parse(filter)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"tag:len/equals/1",
		"config->pinned:len/after/0",
		"external->ids[0]/after/abc",
		"defaults->a:num/after/1",
		"schema->type/equals/a",
	})
	void testTagOnlyFilter(String filter) {
		assertThat(filter).matches(Filter.REGEX);
		assertThat(filter).matches(Filter.TAG_REGEX);
		assertThat(filter).doesNotMatch(Filter.REF_REGEX);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"published/after/2024-01-01T00:00:00Z",
		"created/after/2024-01-01T00:00:00Z",
		"tags:len/equals/1",
		"sources:len/equals/1",
		"alternateUrls:len/equals/1",
		"metadata->responses/after/3",
		"metadata->newResponse/after/2024-01-01T00:00:00Z",
		"metadata->plugins->plugin/comment/after/0",
		"plugins->plugin/user/vote:top/after/5",
		"plugins->plugin/test->size:num/after/1",
		"tags->plugin/duration:dur/after/PT10M",
	})
	void testRefOnlyFilter(String filter) {
		assertThat(filter).matches(Filter.REGEX);
		assertThat(filter).matches(Filter.REF_REGEX);
		assertThat(filter).doesNotMatch(Filter.TAG_REGEX);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"modified/after/2024-01-01T00:00:00Z",
		"origin:len/after/1",
	})
	void testSharedFilter(String filter) {
		assertThat(filter).matches(Filter.REF_REGEX);
		assertThat(filter).matches(Filter.TAG_REGEX);
	}
}
