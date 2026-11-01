package jasper.repository.spec;

import jasper.repository.spec.SortSpec.PluginValueFilter;
import jasper.repository.spec.SortSpec.TagValueFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SortSpecTest {

	@Test
	void testTagValueFilter() {
		var f = TagValueFilter.parse("tags->plugin/title/kiwi");
		assertThat(f.sort().function()).isEqualTo("tag_value");
		assertThat(f.sort().tag()).isEqualTo("plugin/title");
		assertThat(f.value()).isEqualTo("kiwi");
		assertThat("tags->plugin/title/kiwi").matches(TagValueFilter.REGEX);
	}

	@Test
	void testTagValueNumFilter() {
		var f = TagValueFilter.parse("tags->plugin/progress:num/2.5");
		assertThat(f.sort().function()).isEqualTo("tag_value_num");
		assertThat(f.sort().tag()).isEqualTo("plugin/progress");
		assertThat(f.value()).isEqualTo(new BigDecimal("2.5"));
		assertThat("tags->plugin/progress:num/2.5").matches(TagValueFilter.REGEX);
	}

	@Test
	void testTagValueDurFilter() {
		var f = TagValueFilter.parse("tags->_plugin/duration:dur/pt1h");
		assertThat(f.sort().function()).isEqualTo("tag_value_dur");
		assertThat(f.sort().tag()).isEqualTo("_plugin/duration");
		assertThat(f.value()).isEqualTo(BigDecimal.valueOf(3600.0));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"plugin/title/kiwi",
		"tags->plugin",
		"tags->plugin/",
		"tags->plugin/progress/50:num",
		"tags->plugin/progress:num/abc",
		"tags->plugin/progress:num/-1",
		"tags->plugin/duration:dur/PT1H",
		"tags->plugin/title/a,b",
		"tags->plugin/title=kiwi",
	})
	void testInvalidTagValueFilter(String filter) {
		assertThat(TagValueFilter.parse(filter)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"plugin/title/kiwi",
		"tags->plugin",
		"tags->plugin/",
		"tags->plugin/progress/50:num",
		"tags->plugin/progress:num/-1",
		"tags->plugin/duration:dur/PT1H",
		"tags->plugin/title/a,b",
		"tags->plugin/title=kiwi",
	})
	void testInvalidTagValueFilterRegex(String filter) {
		assertThat(filter).doesNotMatch(TagValueFilter.REGEX);
	}

	@Test
	void testPluginValueFilter() {
		var f = PluginValueFilter.parse("plugins->plugin/test->style->color/blue");
		assertThat(f.plugin()).isEqualTo("plugin/test");
		assertThat(f.property()).isEqualTo("plugins->plugin/test->style->color");
		assertThat(f.value()).isEqualTo("blue");
	}

	@Test
	void testPluginValueFilterSlashInValue() {
		var f = PluginValueFilter.parse("plugins->plugin/test->url/https://example.com/a->b");
		assertThat(f.plugin()).isEqualTo("plugin/test");
		assertThat(f.property()).isEqualTo("plugins->plugin/test->url");
		assertThat(f.value()).isEqualTo("https://example.com/a->b");
		assertThat("plugins->plugin/test->url/https://example.com/a->b").matches(PluginValueFilter.REGEX);
	}

	@Test
	void testPluginValueFilterNum() {
		var f = PluginValueFilter.parse("plugins->_plugin/cache->contentLength:num/-3.5");
		assertThat(f.plugin()).isEqualTo("_plugin/cache");
		assertThat(f.property()).isEqualTo("plugins->_plugin/cache->contentLength:num");
		assertThat(f.value()).isEqualTo(new BigDecimal("-3.5"));
	}

	@Test
	void testPluginValueFilterLen() {
		var f = PluginValueFilter.parse("plugins->plugin/test->ids:len/3");
		assertThat(f.property()).isEqualTo("plugins->plugin/test->ids:len");
		assertThat(f.value()).isEqualTo(new BigDecimal("3"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"plugin/test->size/1",
		"plugins->plugin/test->size",
		"plugins->plugin/test->size/",
		"plugins->plugin/test->size=1",
		"plugins->plugin/test->size:num/abc",
		"plugins->plugin/test->ids:len/-1",
		"plugins->plugin/test->it's/1",
		"plugins->plugin/test->name/a,b",
		"plugins->custom->size/1",
		"plugins->plugin/test/value",
		"plugins->plugin/test:num/5",
	})
	void testInvalidPluginValueFilter(String filter) {
		assertThat(PluginValueFilter.parse(filter)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"plugin/test->size/1",
		"plugins->plugin/test->size",
		"plugins->plugin/test->size/",
		"plugins->plugin/test->size=1",
		"plugins->plugin/test->it's/1",
		"plugins->plugin/test->name/a,b",
		"plugins->custom->size/1",
		"plugins->plugin/test/value",
		"plugins->plugin/test:num/5",
	})
	void testInvalidPluginValueFilterRegex(String filter) {
		assertThat(filter).doesNotMatch(PluginValueFilter.REGEX);
	}
}
