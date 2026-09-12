package jasper.component.channel;

import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.service.dto.PluginDto;
import jasper.service.dto.TemplateDto;
import jasper.service.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClearConfigCacheTest {

	@Mock
	Props props;

	@Mock
	TaskScheduler taskScheduler;

	@Mock
	ConfigCache configs;

	@InjectMocks
	ClearConfigCache clearConfigCache;

	@Test
	void entityUpdatesClearOnlyTheirOrigins() {
		var user = new UserDto();
		user.setOrigin("@user");
		var plugin = new PluginDto();
		plugin.setOrigin("@plugin");
		var template = new TemplateDto();
		template.setOrigin("@template");

		clearConfigCache.handleUserUpdate(MessageBuilder.withPayload(user).build());
		clearConfigCache.handlePluginUpdate(MessageBuilder.withPayload(plugin).build());
		clearConfigCache.handleTemplateUpdate(MessageBuilder.withPayload(template).build());

		verify(configs).clearUserCache("@user");
		verify(configs).clearPluginCache("@plugin");
		verify(configs).clearTemplateCache("@template");
	}

	@Test
	void configUpdatesHaveIndependentOriginCooldowns() {
		when(configs.isConfigTag("+config")).thenReturn(true);

		clearConfigCache.handleTagUpdate(MessageBuilder.withPayload("value")
			.setHeader("tag", "+config")
			.setHeader("origin", "@tenant")
			.build());
		clearConfigCache.handleTagUpdate(MessageBuilder.withPayload("value")
			.setHeader("tag", "+config")
			.setHeader("origin", "@other")
			.build());

		verify(configs).clearConfigCache("@tenant");
		verify(configs).clearConfigCache("@other");
	}
}
