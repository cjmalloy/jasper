package jasper.component.scheduler;

import jasper.component.Remotes;
import jasper.config.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Profile("pull-burst")
@Component
public class PullBurst {
	private static final Logger logger = LoggerFactory.getLogger(PullBurst.class);

	@Autowired
	Remotes remotes;

	@Autowired
	Props props;

	@Scheduled(
		fixedRateString = "${jasper.replicate-interval-min}",
		initialDelayString = "${jasper.replicate-delay-min}",
		timeUnit = TimeUnit.MINUTES)
	public void burst() {
		for (var origin : props.getReplicateOrigins()) {
			logger.info("Pulling all {} remotes in a burst.", origin);
			while (remotes.pull(origin));
			logger.info("All {} remotes pulled.", origin);
		}
	}

}
