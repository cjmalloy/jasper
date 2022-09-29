package jasper.component;

import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Profile("repl-schedule")
@Component
public class OriginScraperSchedule {
	private static final Logger logger = LoggerFactory.getLogger(OriginScraperSchedule.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Replicator replicator;

	@Scheduled(
		fixedRateString = "${jasper.replicate-interval-min}",
		initialDelayString = "${jasper.replicate-delay-min}",
		timeUnit = TimeUnit.MINUTES)
	public void burst() {
		logger.info("Replicating all origins on schedule.");
		var maybeFeed = refRepository.oldestNeedsReplByOrigin("");
		if (maybeFeed.isEmpty()) {
			logger.info("All origins up to date.");
			return;
		}
		replicator.replicate(maybeFeed.get());
	}

}
