package jasper.component.k8s;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import jasper.config.Props;
import jasper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Profile("kubernetes")
@Component
public class TunnelServer {
	private static final Logger logger = LoggerFactory.getLogger(TunnelServer.class);

	@Autowired
	Props props;

	@Autowired
	UserRepository userRepository;

	Map<String, Instant> lastModified = new HashMap<>();

	@Scheduled(
		fixedRateString = "${jasper.async-interval-sec}",
		initialDelayString = "${jasper.async-delay-sec}",
		timeUnit = TimeUnit.SECONDS)
	public void generateConfig() {
		var changed = false;
		StringBuilder result = new StringBuilder();
		for (var origin : props.getSshOrigins()) {
			var cursor = userRepository.getCursor(origin);
			if (cursor != null) {
				changed |= !lastModified.containsKey(origin) || lastModified.get(origin).isBefore(cursor);
				lastModified.put(origin, cursor);
			}
		}
		if (!changed) return;
		logger.info("Generating new authorized_keys");
		for (var origin : props.getSshOrigins()) {
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
				.createOrReplace(new ConfigMapBuilder()
					.withNewMetadata()
						.withName(props.getSshConfigMapName())
					    .withNamespace(props.getSshConfigNamespace())
					.and()
					.addToData("authorized_keys", result.toString())
					.build());
		}
	}

}
