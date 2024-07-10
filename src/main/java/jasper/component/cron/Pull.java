package jasper.component.cron;

import jasper.component.ConfigCache;
import jasper.component.Remotes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static jasper.domain.proj.HasOrigin.formatOrigin;

@Component
public class Pull {
	private static final Logger logger = LoggerFactory.getLogger(Pull.class);

	@Autowired
	Remotes remotes;

	@Autowired
	ConfigCache configs;

	@Scheduled(
		fixedRateString = "${jasper.pull-interval-min}",
		initialDelayString = "${jasper.pull-delay-min}",
		timeUnit = TimeUnit.MINUTES)
	public void burst() {
		var root = configs.root();
		for (var origin : root.getPullOrigins()) {
			logger.info("Pulling {} {} remotes", root.getPullBatchSize(), formatOrigin(origin));
			for (var i = 0; i < root.getScrapeBatchSize(); i++) {
				if (!remotes.pull(origin)) {
					logger.info("All {} remotes pulled.", formatOrigin(origin));
					break;
				}
			}
			logger.info("Finished pulling {} remotes.", formatOrigin(origin));
		}
	}

}
