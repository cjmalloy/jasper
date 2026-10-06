package jasper.repository.filter;

import jasper.errors.InvalidQueryException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
	})
	void testParseMalformed(String query) {
		assertThatThrownBy(() -> new TagQuery(query))
			.isInstanceOf(InvalidQueryException.class);
	}
}
