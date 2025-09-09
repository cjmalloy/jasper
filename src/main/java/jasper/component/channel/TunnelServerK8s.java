package jasper.component.channel;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import jasper.component.ConfigCache;
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

import static jasper.repository.spec.QualifiedTag.concat;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Profile("kubernetes")
@Component
public class TunnelServerK8s {
	private static final Logger logger = LoggerFactory.getLogger(TunnelServerK8s.class);

	@Autowired
	Props props;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ConfigCache configs;

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
		try (var client = new DefaultKubernetesClient()) {
			client.secrets()
				.inNamespace(props.getSshConfigNamespace())
				.resource(new SecretBuilder()
					.withNewMetadata()
					.withName(props.getSshSecretName())
					.and()
					.addToStringData("host_key", hostKey)
					.build())
				.serverSideApply();
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
		try (var client = new DefaultKubernetesClient()) {
			client.configMaps()
				.inNamespace(props.getSshConfigNamespace())
				.resource(new ConfigMapBuilder()
					.withNewMetadata()
						.withName(props.getSshConfigMapName())
					.and()
					.addToData("authorized_keys", result.toString())
					.build())
				.serverSideApply();
		}
	}

}
