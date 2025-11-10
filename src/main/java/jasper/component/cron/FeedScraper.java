package jasper.component.cron;

import jakarta.annotation.PostConstruct;
import jasper.component.script.RssParser;
import jasper.domain.Ref;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Profile("scripts")
@Component
public class FeedScraper implements Cron.CronRunner {

	@Autowired
	Cron cron;

	@Autowired
	RssParser rssParser;

	@PostConstruct
	void init() {
		cron.addCronTag("plugin/feed", this);
	}

	@Async
	public void run(Ref ref) {
		rssParser.runScript(ref, "plugin/feed");
	}

}
