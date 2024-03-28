package jasper.component.delta;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import jasper.component.ConfigCache;
import jasper.config.Props;
import jasper.plugin.Config;
import jasper.repository.UserRepository;
import jasper.service.dto.TemplateDto;
import jasper.service.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Profile("kubernetes")
@Component
public class TunnelServer {
	private static final Logger logger = LoggerFactory.getLogger(TunnelServer.class);

	@Autowired
	Props props;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ConfigCache configs;

	Config root() {
		return configs.getTemplate("_config", "", Config.class);
	}

	@ServiceActivator(inputChannel = "userRxChannel")
	public void handleUserUpdate(Message<UserDto> message) {
		generateConfig();
	}

	@ServiceActivator(inputChannel = "templateRxChannel")
	public void handleTemplateUpdate(Message<TemplateDto> message) {
		if (isBlank((String) message.getHeaders().get("origin")) && message.getPayload().getTag().equals("_config")) {
			generateConfig();
		}
	}

	public void generateConfig() {
		logger.info("Generating new authorized_keys");
		var root = root();
		var result = new StringBuilder();
		for (var origin : root.getSshOrigins()) {
			result
				.append("\n# ")
				.append(isBlank(origin) ? "default" : origin)
				.append("\n");
			for (var u : userRepository.findAllByOriginAndPubKeyIsNotNull(origin)) {
				if (u.getPubKey().length == 0) continue;
				var parts = new String(u.getPubKey()).split("\\s+");
				result
					.append(parts[0])
					.append(" ")
					.append(parts[1])
					.append(" ")
					.append(u.getQualifiedTag())
					.append("\n");
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
