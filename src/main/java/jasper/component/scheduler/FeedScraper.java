package jasper.component.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.io.FeedException;
import jasper.component.ConfigCache;
import jasper.component.RssParser;
import jasper.component.Tagger;
import jasper.domain.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Component
public class FeedScraper implements Scheduler.CronRunner {
	private static final Logger logger = LoggerFactory.getLogger(FeedScraper.class);

	@Autowired
	Scheduler cron;

	@Autowired
	RssParser rssParser;

	@Autowired
	Tagger tagger;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ConfigCache configs;

	@PostConstruct
	void init() {
		cron.addCronTag("plugin/feed", this);
	}

	public void run(Ref ref) {
		try {
			logger.info("{} Scraping {} feed: {}.", ref.getOrigin(), ref.getTitle(), ref.getUrl());
			rssParser.scrape(ref, false);
			logger.info("{} Finished scraping feed: {}.", ref.getOrigin(), ref.getUrl());
		} catch (IOException e) {
			tagger.attachError(ref.getUrl(), ref.getOrigin(), "Error loading feed", e.getMessage());
		} catch (FeedException e) {
			tagger.attachError(ref.getUrl(), ref.getOrigin(), "Error parsing feed", e.getMessage());
		} catch (Throwable e) {
			tagger.attachError(ref.getUrl(), ref.getOrigin(), "Unexpected error scraping feed", e.getMessage());
		}
	}

}
