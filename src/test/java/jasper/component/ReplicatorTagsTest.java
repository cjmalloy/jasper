package jasper.component;

import org.junit.jupiter.api.Test;

import static jasper.component.Replicator.deletedTag;
import static jasper.component.Replicator.deletorTag;
import static org.assertj.core.api.Assertions.assertThat;

class ReplicatorTagsTest {

	@Test
	void testDeletorTag() {
		assertThat(deletorTag("")).isEqualTo("deleted");
		assertThat(deletorTag("@sub")).isEqualTo("deleted@sub");
		assertThat(deletorTag("test")).isEqualTo("test/deleted");
		assertThat(deletorTag("test@sub")).isEqualTo("test/deleted@sub");
	}

	@Test
	void testDeletedTag() {
		assertThat(deletedTag("deleted")).isEqualTo("");
		assertThat(deletedTag("deleted@sub")).isEqualTo("@sub");
		assertThat(deletedTag("test/deleted")).isEqualTo("test");
		assertThat(deletedTag("test/deleted@sub")).isEqualTo("test@sub");
	}
}
