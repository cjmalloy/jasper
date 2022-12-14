package jasper.component.cron;

import jakarta.annotation.PostConstruct;
import jasper.component.ConfigCache;
import jasper.component.Replicator;
import jasper.domain.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
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

	public void run(Ref ref) {
		var root = configs.root();
		if (!root.getPullOrigins().contains(ref.getOrigin())) return;
		logger.info("{} Pulling origin {}: {}", ref.getOrigin(), ref.getTitle(), ref.getUrl());
		replicator.pull(ref);
	}

}
