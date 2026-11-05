package jasper.component;

import jasper.config.Config.ServerConfig;
import jasper.config.Props;
import jasper.service.dto.TemplateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.messaging.support.MessageBuilder.withPayload;

public class ConfigCacheRootUpdateTest {

	ConfigCache configCache = new ConfigCache();
	ServerConfig root = ServerConfig.builder().build();
	ArrayList<ServerConfig> updates = new ArrayList<>();

	@BeforeEach
	void init() {
		configCache.props = new Props();
		configCache.self = mock(ConfigCache.class);
		when(configCache.self.root()).thenReturn(root);
		configCache.rootUpdate(updates::add);
		updates.clear();
	}

	void update(String tag, String origin) {
		var template = new TemplateDto();
		template.setTag(tag);
		template.setOrigin(origin);
		configCache.handleTemplateUpdate(withPayload(template).build());
	}

	@Test
	void testServerConfigUpdate() {
		update("_config/server", "");

		assertThat(updates).containsExactly(root);
	}

	@Test
	void testIgnoresOtherTemplates() {
		update("_config/index", "");
		update("_config/server", "@other");

		assertThat(updates).isEmpty();
	}

	@Test
	void testServerConfigUpdateInLocalOrigin() {
		configCache.props.setLocalOrigin("@tenant");

		update("_config/server", "");
		update("_config/server", "@tenant");

		assertThat(updates).containsExactly(root);
	}

	@Test
	void testWorkerServerConfigUpdate() {
		configCache.props.setWorker("worker-0");
		configCache.props.setWorkload(new String[]{ "@feeds" });

		update("_config/server", "");
		update("_config/server/feeds", "");

		assertThat(updates).containsExactly(root);
	}
}
