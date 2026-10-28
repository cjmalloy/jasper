package jasper.domain.proj;

import org.junit.jupiter.api.Test;

import static jasper.domain.proj.Tag.qualifiedUserUrl;
import static jasper.domain.proj.Tag.userUrlOrigin;
import static jasper.domain.proj.Tag.userUrlPrefix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TagTest {

	@Test
	void testUserUrlPrefixDefaultOrigin() {
		assertThat(userUrlPrefix("+user/tester", "")).isEqualTo("tag:/user/tester?url=");
		assertThat(userUrlPrefix("+user/tester", null)).isEqualTo("tag:/user/tester?url=");
		assertThat(userUrlPrefix("+user/tester", "@")).isEqualTo("tag:/user/tester?url=");
	}

	@Test
	void testUserUrlPrefixOrigin() {
		assertThat(userUrlPrefix("_user/tester", "@a.b")).isEqualTo("tag:/user/tester@a.b?url=");
	}

	@Test
	void testUserUrlPrefixRejectsSelectors() {
		assertThatThrownBy(() -> userUrlPrefix("+user/tester", "@*"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> userUrlPrefix("+user/tester", "@foo.*"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testQualifiedUserUrl() {
		assertThat(qualifiedUserUrl("tag:/user/tester?url=x", "")).isEqualTo("tag:/user/tester?url=x");
		assertThat(qualifiedUserUrl("tag:/user/tester?url=x", "@")).isEqualTo("tag:/user/tester?url=x");
		assertThat(qualifiedUserUrl("tag:/user/tester?url=x", "@a")).isEqualTo("tag:/user/tester@a?url=x");
		assertThat(qualifiedUserUrl("tag:/user/tester", "@a")).isEqualTo("tag:/user/tester@a");
		assertThatThrownBy(() -> qualifiedUserUrl("tag:/user/tester", "@*"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testUserUrlOrigin() {
		assertThat(userUrlOrigin("tag:/user/tester?url=x@y")).isEqualTo("");
		assertThat(userUrlOrigin("tag:/user/tester@a?url=x@y")).isEqualTo("@a");
		assertThat(userUrlOrigin("tag:/user/tester@a.b")).isEqualTo("@a.b");
		assertThat(userUrlOrigin("tag:/user/tester")).isEqualTo("");
	}

	@Test
	void testQualifiedUserUrlNormalizesUserTag() {
		assertThat(qualifiedUserUrl("tag:/+user/tester?url=x", "@a")).isEqualTo("tag:/user/tester@a?url=x");
		assertThat(qualifiedUserUrl("tag:/_user/tester?url=x", "")).isEqualTo("tag:/user/tester?url=x");
		assertThat(qualifiedUserUrl("tag:/+user?url=x", "")).isEqualTo("tag:/user?url=x");
		assertThat(qualifiedUserUrl("tag:/+username?url=x", "")).isEqualTo("tag:/+username?url=x");
	}
}
