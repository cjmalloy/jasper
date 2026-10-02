package jasper.component.channel;

import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.domain.proj.HasOrigin;
import jasper.service.dto.PluginDto;
import jasper.service.dto.TemplateDto;
import jasper.service.dto.UserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ClearConfigCache {

	@Autowired
	Props props;

	@Autowired
	TaskScheduler taskScheduler;

	@Autowired
	ConfigCache configs;

	private final ConcurrentMap<String, AtomicBoolean> clearingConfig = new ConcurrentHashMap<>();

	@Order(Ordered.HIGHEST_PRECEDENCE)
	@ServiceActivator(inputChannel = "tagRxChannel")
	public void handleTagUpdate(Message<String> message) {
		if (!configs.isConfigTag((String) message.getHeaders().get("tag"))) return;
		var origin = HasOrigin.origin(Objects.toString(message.getHeaders().get("origin"), ""));
		var clearNow = new AtomicBoolean();
		clearingConfig.compute(origin, (key, clearAgain) -> {
			if (clearAgain == null) {
				clearNow.set(true);
				return new AtomicBoolean();
			}
			clearAgain.set(true);
			return clearAgain;
		});
		if (clearNow.get()) clearConfig(origin);
	}

	private void checkIfClearingAgain(String origin) {
		var clearAgain = clearingConfig.computeIfPresent(origin, (key, state) ->
			state.getAndSet(false) ? state : null
		);
		if (clearAgain != null) clearConfig(origin);
	}

	private void clearConfig(String origin) {
		configs.clearConfigCache(origin);
		taskScheduler.schedule(() -> checkIfClearingAgain(origin), Instant.now().plusMillis(props.getClearCacheCooldownSec() * 1000L));
	}

	@Order(Ordered.HIGHEST_PRECEDENCE)
	@ServiceActivator(inputChannel = "userRxChannel")
	public void handleUserUpdate(Message<UserDto> message) {
		configs.clearUserCache(message.getPayload().getOrigin());
	}

	@Order(Ordered.HIGHEST_PRECEDENCE)
	@ServiceActivator(inputChannel = "pluginRxChannel")
	public void handlePluginUpdate(Message<PluginDto> message) {
		configs.clearPluginCache(message.getPayload().getOrigin());
	}

	@Order(Ordered.HIGHEST_PRECEDENCE)
	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<TemplateDto> message) {
		configs.clearTemplateCache(message.getPayload().getOrigin());
	}
}
