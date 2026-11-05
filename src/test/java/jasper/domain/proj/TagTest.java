package jasper.domain.proj;

import org.junit.jupiter.api.Test;

import static jasper.domain.proj.Tag.qualifiedUser;
import static jasper.domain.proj.Tag.userUrlUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TagTest {

	@Test
	void testQualifiedUserDefaultOrigin() {
		assertThat(qualifiedUser("+user/tester", "")).isEqualTo("user/tester");
		assertThat(qualifiedUser("+user/tester", null)).isEqualTo("user/tester");
		assertThat(qualifiedUser("+user/tester", "@")).isEqualTo("user/tester");
	}

	@Test
	void testQualifiedUserOrigin() {
		assertThat(qualifiedUser("_user/tester", "@a.b")).isEqualTo("user/tester@a.b");
	}

	@Test
	void testQualifiedUserRejectsSelectors() {
		assertThatThrownBy(() -> qualifiedUser("+user/tester", "@*"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> qualifiedUser("+user/tester", "@foo.*"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testUserUrlUser() {
		assertThat(userUrlUser("tag:/user/tester?url=x", "", "x")).isEqualTo("user/tester");
		assertThat(userUrlUser("tag:/user/tester?url=x", "@", "x")).isEqualTo("user/tester");
		assertThat(userUrlUser("tag:/user/tester?url=x", "@a", "x")).isEqualTo("user/tester@a");
		assertThatThrownBy(() -> userUrlUser("tag:/user/tester?url=x", "@*", "x"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testUserUrlUserOnlyForTheRef() {
		assertThat(userUrlUser("tag:/user/tester?url=y", "", "x")).isNull();
		assertThat(userUrlUser("tag:/user/tester?url=x?url=x", "", "x")).isNull();
		assertThat(userUrlUser("tag:/user/tester", "", "x")).isNull();
		assertThat(userUrlUser("tag:/news?url=x", "", "x")).isNull();
		assertThat(userUrlUser("https://example.com", "", "x")).isNull();
	}

	@Test
	void testUserUrlUserNormalizesUserTag() {
		assertThat(userUrlUser("tag:/+user/tester?url=x", "@a", "x")).isEqualTo("user/tester@a");
		assertThat(userUrlUser("tag:/_user/tester?url=x", "", "x")).isEqualTo("user/tester");
		assertThat(userUrlUser("tag:/+user?url=x", "", "x")).isEqualTo("user");
		assertThat(userUrlUser("tag:/+username?url=x", "", "x")).isNull();
	}
}
