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

@Component("cronPull")
public class Pull implements Scheduler.CronRunner  {
	private static final Logger logger = LoggerFactory.getLogger(Pull.class);

	@Autowired
	Scheduler cron;

	@Autowired
	Replicator replicator;

	@Autowired
	ConfigCache configs;

	@PostConstruct
	void init() {
		cron.addCronTag("+plugin/origin/pull", this);
	}

	public void run(Ref remote) {
		var root = configs.root();
		if (!root.getPullOrigins().contains(remote.getOrigin())) return;
		var config = getOrigin(remote);
		var localOrigin = subOrigin(remote.getOrigin(), config.getLocal());
		logger.info("{} Pulling origin ({}) on schedule {}: {}", remote.getOrigin(), formatOrigin(localOrigin), remote.getTitle(), remote.getUrl());
		replicator.pull(remote);
		logger.info("{} Finished pulling origin ({}) on schedule {}: {}", remote.getOrigin(), formatOrigin(localOrigin), remote.getTitle(), remote.getUrl());
	}

}
