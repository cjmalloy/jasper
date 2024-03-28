package jasper.component.scheduler;

import jasper.component.ConfigCache;
import jasper.component.Remotes;
import jasper.plugin.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static jasper.domain.proj.HasOrigin.formatOrigin;

@Profile("push-burst")
@Component
public class PushBurst {
	private static final Logger logger = LoggerFactory.getLogger(PushBurst.class);

	@Autowired
	Remotes remotes;

	@Autowired
	ConfigCache configs;

	Config root() {
		return configs.getTemplate("_config", "", Config.class);
	}

	@Scheduled(
		fixedRateString = "${jasper.replicate-interval-min}",
		initialDelayString = "${jasper.replicate-delay-min}",
		timeUnit = TimeUnit.MINUTES)
	public void burst() {
		logger.info("Pushing all remotes in a burst.");
		for (var origin : root().getReplicateOrigins()) {
			logger.info("Pushing all {} remotes in a burst.", formatOrigin(origin));
			while (remotes.push(origin));
			logger.info("All {} remotes pushed.", formatOrigin(origin));
		}
	}

}
