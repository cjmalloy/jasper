package jasper.component.delta;

import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.plugin.Root;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component
public class ClearConfigCache {

	@Autowired
	Props props;

	@Autowired
	ConfigCache configs;

	Root root() {
		return configs.getTemplate("", "", Root.class);
	}

	@ServiceActivator(inputChannel = "tagRxChannel")
	public void handleTagUpdate(Message<String> message) {
		if (root().getCacheTags().contains((String) message.getHeaders().get("tag"))) {
			configs.clearConfigCache();
		}
	}

	@ServiceActivator(inputChannel = "userRxChannel")
	public void handleUserUpdate(Message<String> message) {
		if (props.getSecurity().hasClient((String) message.getHeaders().get("origin"))) {
			configs.clearUserCache();
		}
	}

	@ServiceActivator(inputChannel = "pluginRxChannel")
	public void handlePluginUpdate(Message<String> message) {
		if (props.getSecurity().hasClient((String) message.getHeaders().get("origin"))) {
			configs.clearConfigCache();
		}
	}

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<String> message) {
		if (props.getSecurity().hasClient((String) message.getHeaders().get("origin"))) {
			configs.clearConfigCache();
		}
	}
}
