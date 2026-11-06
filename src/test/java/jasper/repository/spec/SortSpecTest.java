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
		var f = Filter.parse(filter, Op.AFTER);
		assertThat(f).isNotNull();
		assertThat(f.column()).isEqualTo(column);
		assertThat(f.op()).isEqualTo(Op.AFTER);
		assertThat(f.value()).isEqualTo(value);
		assertThat(filter).matches(Filter.REGEX);
	}

	@Test
	void testDateFilter() {
		assertFilter("modified/2024-01-01T00:00:00Z", "modified", Instant.parse("2024-01-01T00:00:00Z"));
		assertFilter("published/2024-01-01T00:00:00.123Z", "published", Instant.parse("2024-01-01T00:00:00.123Z"));
		assertFilter("created/2024-01-01T00:00:00Z", "created", Instant.parse("2024-01-01T00:00:00Z"));
	}

	@Test
	void testMetadataDateFilter() {
		assertFilter("metadata->newResponse/2024-01-01T00:00:00Z", "metadata->newResponse", "2024-01-01T00:00:00.000000000Z");
		assertFilter("metadata->newReaction/2024-01-01T00:00:00.5Z", "metadata->newReaction", "2024-01-01T00:00:00.500000000Z");
	}

	@Test
	void testResponseCountFilter() {
		assertFilter("metadata->plugins->plugin/comment/0", "metadata->plugins->plugin/comment", new BigDecimal("0"));
		assertFilter("metadata->remotePlugins->plugin/user/read:num/10", "metadata->remotePlugins->plugin/user/read:num", new BigDecimal("10"));
		assertFilter("metadata->responses/3", "metadata->responses", 3);
	}

	@Test
	void testLengthFilter() {
		assertFilter("tags:len/3", "tags:len", 3);
		assertFilter("origin:len/1", "origin:len", 1);
		assertFilter("tag:len/2", "tag:len", 2);
	}

	@Test
	void testVoteFilter() {
		assertFilter("plugins->plugin/user/vote:top/5", "plugins->plugin/user/vote:top", new BigDecimal("5"));
	}

	@Test
	void testTagValueFilter() {
		assertFilter("tags->plugin/title/kiwi", "tags->plugin/title", "kiwi");
		assertFilter("tags->plugin/progress:num/2.5", "tags->plugin/progress:num", new BigDecimal("2.5"));
		assertFilter("tags->_plugin/duration:dur/pt1h", "tags->_plugin/duration:dur", BigDecimal.valueOf(3600.0));
		assertFilter("tags->plugin/duration:dur/PT1H", "tags->plugin/duration:dur", BigDecimal.valueOf(3600.0));
	}

	@Test
	void testPluginValueFilter() {
		assertFilter("plugins->plugin/test->style->color/blue", "plugins->plugin/test->style->color", "blue");
		assertFilter("plugins->plugin/test->url/https:", "plugins->plugin/test->url", "https:");
		assertFilter("plugins->_plugin/cache->contentLength:num/-3.5", "plugins->_plugin/cache->contentLength:num", new BigDecimal("-3.5"));
		assertFilter("plugins->plugin/test->ids:len/3", "plugins->plugin/test->ids:len", 3);
		assertFilter("plugins->plugin/a/b/c", "plugins->plugin/a/b", "c");
		assertFilter("plugins->plugin:len/3", "plugins->plugin:len", 3);
	}

	@Test
	void testConfigValueFilter() {
		assertFilter("config->pinned:len/0", "config->pinned:len", 0);
		assertFilter("external->ids[0]/abc", "external->ids[0]", "abc");
		assertFilter("schema->properties->name->type/string", "schema->properties->name->type", "string");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"modified/2024-01-01",
		"modified/yesterday",
		"metadata->newResponse/2024-13-01T00:00:00Z",
		"metadata->plugins->plugin/comment/abc",
		"metadata->responses/-1",
		"tags:len/x",
		"tags->plugin/progress:num/abc",
		"plugins->plugin/test->ids:len/-1",
		"plugins->plugin/test->size:num/abc",
	})
	void testInvalidFilterValue(String filter) {
		assertThat(Filter.parse(filter, Op.BEFORE)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"plugin/test->size/1",
		"title/abc",
		"url/abc",
		"metadata->modified/2024-01-01T00:00:00Z",
		"metadata->userUrls/abc",
		"tags->plugin",
		"tags->plugin/",
		"tags->plugin/title/a,b",
		"plugins->plugin/test->size",
		"plugins->plugin/test->size/",
		"plugins->plugin/test->size=1",
		"plugins->plugin/test->it's/1",
		"plugins->plugin/test->name/a,b",
		"plugins->custom->size/1",
		"plugins->plugin/test->url/https://a.com",
		"plugins->plugin/test:dur/pt1h",
		"plugins->plugin",
		"plugins->plugin/",
		"config/1",
		"other->a/1",
	})
	void testInvalidFilter(String filter) {
		assertThat(filter).doesNotMatch(Filter.REGEX);
		assertThat(Filter.parse(filter, Op.EQUALS)).isNull();
	}
}
