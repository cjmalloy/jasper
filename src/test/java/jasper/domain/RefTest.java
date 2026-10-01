package jasper.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefTest {

	@Test
	void testHasPluginFalseForJsonNull() throws Exception {
		var ref = new Ref();
		ref.setPlugins((ObjectNode) new ObjectMapper().readTree("""
		{
			"plugin/null": null,
			"plugin/data": {}
		}"""));

		assertThat(ref.hasPlugin("plugin/null"))
			.isFalse();
		assertThat(ref.hasPlugin("plugin/missing"))
			.isFalse();
		assertThat(ref.hasPlugin("plugin/data"))
			.isTrue();
	}
}
