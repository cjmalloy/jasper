package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.domain.Ref;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

import static jasper.service.TaggingService.touchesPluginRoot;
import static org.assertj.core.api.Assertions.assertThat;

class TaggingServiceTest {

	static final ObjectMapper objectMapper = new ObjectMapper();

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
		add     | /plugin~1test          | true
		add     | /plugin~1test/color    | false
		add     | ''                     | true
		add     | /plugin~1other         | false
		add     | /plugin~1test2         | false
		replace | /plugin~1test          | true
		replace | /plugin~1test/color    | false
		replace | ''                     | true
		replace | /plugin~1other         | false
		replace | /plugin~1test2         | false
		remove  | /plugin~1test          | true
		remove  | /plugin~1test/color    | false
		remove  | ''                     | true
		remove  | /plugin~1other         | false
		remove  | /plugin~1test2         | false
		copy    | /plugin~1test          | true
		copy    | /plugin~1test/color    | false
		copy    | ''                     | true
		copy    | /plugin~1other         | false
		copy    | /plugin~1test2         | false
		move    | /plugin~1test          | true
		move    | /plugin~1test/color    | false
		move    | ''                     | true
		move    | /plugin~1other         | false
		move    | /plugin~1test2         | false
		test    | /plugin~1test          | false
		test    | /plugin~1test/color    | false
		test    | ''                     | false
		test    | /plugin~1other         | false
		test    | /plugin~1test2         | false
		""")
	void testTouchesPluginRoot(String op, String path, boolean expected) {
		var operation = objectMapper.createObjectNode()
			.put("op", op)
			.put("path", path)
			.put("value", "x")
			.put("from", "/plugin~1source");

		assertThat(touchesPluginRoot(operation, "plugin/test"))
			.isEqualTo(expected);
	}

	@Test
	void testMoveFromPluginRootTouchesPluginRoot() throws IOException {
		var operation = objectMapper.readTree("""
		{"op": "move", "from": "/plugin~1test", "path": "/plugin~1other"}
		""");

		assertThat(touchesPluginRoot(operation, "plugin/test")).isTrue();
		assertThat(touchesPluginRoot(operation, "plugin/other")).isTrue();
		assertThat(touchesPluginRoot(operation, "plugin/test2")).isFalse();
	}

	@Test
	void testMoveFromNestedOrSiblingPrefixDoesNotTouchPluginRoot() throws IOException {
		assertThat(touchesPluginRoot(objectMapper.readTree("""
		{"op": "move", "from": "/plugin~1test/color", "path": "/plugin~1other/color"}
		"""), "plugin/test")).isFalse();
		assertThat(touchesPluginRoot(objectMapper.readTree("""
		{"op": "move", "from": "/plugin~1test2", "path": "/plugin~1other"}
		"""), "plugin/test")).isFalse();
	}

	@Test
	void testCopyFromPluginRootDoesNotTouchPluginRoot() throws IOException {
		var operation = objectMapper.readTree("""
		{"op": "copy", "from": "/plugin~1test", "path": "/plugin~1other"}
		""");

		assertThat(touchesPluginRoot(operation, "plugin/test")).isFalse();
	}

	@Test
	void testTouchesPluginRootWithEscapedTag() throws IOException {
		var operation = objectMapper.readTree("""
		{"op": "add", "path": "/a~0~1b", "value": null}
		""");

		assertThat(touchesPluginRoot(operation, "a~/b")).isTrue();
		assertThat(touchesPluginRoot(operation, "a~b")).isFalse();
		assertThat(touchesPluginRoot(operation, "a/b")).isFalse();
	}

	@Test
	void testPluginPointerEscaping() {
		assertThat(Ref.pluginPointer("plugin/test")).isEqualTo("/plugin~1test");
		assertThat(Ref.pluginPointer("a~b")).isEqualTo("/a~0b");
		assertThat(Ref.pluginPointer("a~/b")).isEqualTo("/a~0~1b");
		assertThat(Ref.pluginPointer("a/~b")).isEqualTo("/a~1~0b");
	}
}
