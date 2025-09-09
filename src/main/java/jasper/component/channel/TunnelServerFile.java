package jasper.component.channel;

import jasper.component.ConfigCache;
import jasper.component.Storage;
import jasper.config.Props;
import jasper.repository.UserRepository;
import jasper.service.dto.TemplateDto;
import jasper.service.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static jasper.repository.spec.QualifiedTag.concat;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Profile("!kubernetes & storage")
@Component
public class TunnelServerFile {
	private static final Logger logger = LoggerFactory.getLogger(TunnelServerFile.class);
	static final String CONFIG = "config";
	static final String SECRETS = "secrets";

	@Autowired
	Props props;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ConfigCache configs;


	@Autowired
	Storage storage;

	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		if (configs.root().getSshOrigins().isEmpty()) return;
		generateHostKey();
	}

	@ServiceActivator(inputChannel = "userRxChannel")
	public void handleUserUpdate(Message<UserDto> message) {
		if (configs.root().getSshOrigins().isEmpty()) return;
		var user = message.getPayload();
		if ("+user".equals(user.getTag()) && props.getLocalOrigin().equals(user.getOrigin())) {
			generateHostKey();
		}
		if (configs.root().getSshOrigins().contains(user.getOrigin())) {
			generateConfig();
		}
	}

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<TemplateDto> message) {
		if (configs.root().getSshOrigins().isEmpty()) return;
		var template = message.getPayload();
		if (concat("_config/server", props.getWorkerOrigin()).equals(template.getTag() + template.getOrigin())) {
			generateConfig();
		}
	}

	public void generateHostKey() {
		logger.info("Generating new host_key");
		var hostKey = "";
		if (configs.user() == null || configs.user().getKey() != null) {
			hostKey = new String(configs.user().getKey());
		}
		try {
			if (storage.exists("", CONFIG, "host_key")) {
				storage.overwrite("", CONFIG, "host_key", hostKey.getBytes());
			} else {
				storage.storeAt("", CONFIG, "host_key", hostKey.getBytes());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void generateConfig() {
		logger.info("Generating new authorized_keys");
		var result = new StringBuilder();
		for (var origin : configs.root().getSshOrigins()) {
			result
				.append("\n# ")
				.append(isBlank(origin) ? "default" : origin)
				.append("\n");
			for (var u : userRepository.findAllByOriginAndAuthorizedKeysIsNotNull(origin)) {
				if (isBlank(u.getAuthorizedKeys())) continue;
				logger.debug("Enabling SSH access for {}",  u.getQualifiedTag());
				var lines = u.getAuthorizedKeys().split("\n");
				for (var l : lines) {
					if (isBlank(l)) continue;
					var parts = l.split("\\s+");
					result
						.append(parts[0])
						.append(" ")
						.append(parts[1])
						.append(" ")
						.append(u.getQualifiedTag())
						.append("\n");
				}
			}
		}
		try {
			if (storage.exists("", CONFIG, "authorized_keys")) {
				storage.overwrite("", CONFIG, "authorized_keys", result.toString().getBytes());
			} else {
				storage.storeAt("", CONFIG, "authorized_keys", result.toString().getBytes());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
