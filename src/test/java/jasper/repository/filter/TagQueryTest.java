package jasper.repository.filter;

import jasper.errors.InvalidQueryException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static jasper.repository.filter.Query.QUERY_LEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TagQueryTest {

	@ParameterizedTest
	@CsvSource(delimiter = ';', value = {
		"a;                 [\"a\"]",
		"!a;                [\"!a\"]",
		"!@origin;          [\"!@origin\"]",
		"!a@origin;         [\"!a@origin\"]",
		"a:!b|c;            [\"a\",\":\",\"!b\",\"|\",\"c\"]",
		"(a|b):c;           [[\"a\",\"|\",\"b\"],\":\",\"c\"]",
		"!(a|b);            [\"!\",[\"a\",\"|\",\"b\"]]",
		"!(a:b);            [\"!\",[\"a\",\":\",\"b\"]]",
		"!!(a|b);           [\"!\",\"!\",[\"a\",\"|\",\"b\"]]",
		"!(!a);             [\"!\",[\"!a\"]]",
		"!!a;               [\"!\",\"!a\"]",
		"a:!(b|c:d);        [\"a\",\":\",\"!\",[\"b\",\"|\",\"c\",\":\",\"d\"]]",
		"!(a:!(b|c));       [\"!\",[\"a\",\":\",\"!\",[\"b\",\"|\",\"c\"]]]",
		"((a));             [[[\"a\"]]]",
		"!(@city|@town);    [\"!\",[\"@city\",\"|\",\"@town\"]]",
		" a | ! ( b : c ) ; [\"a\",\"|\",\"!\",[\"b\",\":\",\"c\"]]",
	})
	void testParse(String query, String ast) {
		assertThat(new TagQuery(query).ast().toString())
			.isEqualTo(ast);
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"!",
		"a:!",
		"!!",
		"!|a",
		"!:a",
		"a|!:b",
		"(!)",
		"!)",
		"()",
		"a:()",
		"!()",
		"(a|())",
		"(",
		")",
		"(a",
		"a)",
		"(a))",
		"a||b",
		"a|",
		"|a",
		":a",
		"a(b)",
		"(a)(b)",
		"(a)b",
		"a!",
		"a!b",
		"a!!b",
		"!a!",
		"!a!b",
		"a@b!",
		"(a!)",
	})
	void testParseMalformed(String query) {
		assertThatThrownBy(() -> new TagQuery(query))
			.isInstanceOf(InvalidQueryException.class);
	}

	@Test
	void testParseMaxDepth() {
		var depth = TagQuery.MAX_DEPTH;
		assertThat(new TagQuery("(".repeat(depth) + "a" + ")".repeat(depth)).ast().toString())
			.isEqualTo("[".repeat(depth + 1) + "\"a\"" + "]".repeat(depth + 1));
		assertThatThrownBy(() -> new TagQuery("(".repeat(depth + 1) + "a" + ")".repeat(depth + 1)))
			.isInstanceOf(InvalidQueryException.class);
	}

	@Test
	void testParseExcessiveNestingAtQueryLength() {
		assertThatThrownBy(() -> new TagQuery("(".repeat(QUERY_LEN)))
			.isInstanceOf(InvalidQueryException.class);
		assertThatThrownBy(() -> new TagQuery("!(".repeat(QUERY_LEN / 2)))
			.isInstanceOf(InvalidQueryException.class);
		var half = (QUERY_LEN - 1) / 2;
		assertThatThrownBy(() -> new TagQuery("(".repeat(half) + "a" + ")".repeat(half)))
			.isInstanceOf(InvalidQueryException.class);
	}
}
