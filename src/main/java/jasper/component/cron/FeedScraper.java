package jasper.component.cron;

import com.rometools.rome.io.FeedException;
import jakarta.annotation.PostConstruct;
import jasper.component.RssParser;
import jasper.component.Tagger;
import jasper.domain.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Profile("scripts")
@Component
public class FeedScraper implements Scheduler.CronRunner {
	private static final Logger logger = LoggerFactory.getLogger(FeedScraper.class);

	@Autowired
	Scheduler cron;

	@Autowired
	RssParser rssParser;

	@Autowired
	Tagger tagger;

	@PostConstruct
	void init() {
		// TODO: redo on template change
		cron.addCronTag("plugin/feed", this);
	}

	@Async
	public void run(Ref ref) {
		logger.info("{} Scraping {} feed: {}.", ref.getOrigin(), ref.getTitle(), ref.getUrl());
		try {
			rssParser.scrape(ref, false);
		} catch (IOException e) {
			tagger.attachError(ref.getUrl(), ref.getOrigin(), "Error loading feed", e.getMessage());
		} catch (FeedException e) {
			tagger.attachError(ref.getUrl(), ref.getOrigin(), "Error parsing feed", e.getMessage());
		} catch (Throwable e) {
			tagger.attachError(ref.getUrl(), ref.getOrigin(), "Unexpected error scraping feed", e.getMessage());
		}
		logger.info("{} Finished scraping feed: {}.", ref.getOrigin(), ref.getUrl());
	}

}
