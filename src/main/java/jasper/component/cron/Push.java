package jasper.component.cron;

import jakarta.annotation.PostConstruct;
import jasper.component.ConfigCache;
import jasper.component.Replicator;
import jasper.domain.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static jasper.domain.proj.HasOrigin.formatOrigin;
import static jasper.domain.proj.HasOrigin.subOrigin;
import static jasper.plugin.Origin.getOrigin;

@Component("cronPush")
public class Push implements Scheduler.CronRunner  {
	private static final Logger logger = LoggerFactory.getLogger(Push.class);

	@Autowired
	Scheduler cron;

	@Autowired
	Replicator replicator;

	@Autowired
	ConfigCache configs;

	@PostConstruct
	void init() {
		cron.addCronTag("+plugin/origin/push", this);
	}

	public void run(Ref remote) {
		var root = configs.root();
		if (!root.getPushOrigins().contains(remote.getOrigin())) return;
		var config = getOrigin(remote);
		var localOrigin = subOrigin(remote.getOrigin(), config.getLocal());
		logger.info("{} Pushing origin ({}) on schedule {}: {}", remote.getOrigin(), formatOrigin(localOrigin), remote.getTitle(), remote.getUrl());
		replicator.push(remote);
		logger.info("{} Finished pushing origin ({}) on schedule {}: {}", remote.getOrigin(), formatOrigin(localOrigin), remote.getTitle(), remote.getUrl());
	}

}
