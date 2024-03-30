package jasper.component.scheduler;

import jasper.component.ConfigCache;
import jasper.component.Remotes;
import jasper.config.Config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static jasper.domain.proj.HasOrigin.formatOrigin;

@Profile("pull-schedule")
@Component
public class PullSchedule {
	private static final Logger logger = LoggerFactory.getLogger(PullSchedule.class);

	@Autowired
	Remotes remotes;

	@Autowired
	ConfigCache configs;

	ServerConfig root() {
		return configs.getTemplate("_config/server", "",  ServerConfig.class);
	}

	@Scheduled(
		fixedRateString = "${jasper.replicate-interval-min}",
		initialDelayString = "${jasper.replicate-delay-min}",
		timeUnit = TimeUnit.MINUTES)
	public void burst() {
		logger.info("Pulling all remotes on schedule.");
		for (var origin : root().getReplicateOrigins()) {
			logger.info("Pulling all {} remotes on schedule", formatOrigin(origin));
			if (!remotes.pull(origin)) {
				logger.info("All {} remotes pulled.", formatOrigin(origin));
			}
		}
	}

}
