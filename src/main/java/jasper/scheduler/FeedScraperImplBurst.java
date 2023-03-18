package jasper.scheduler;

import jasper.component.FeedScraper;
import jasper.config.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Profile("feed-burst")
@Component
public class FeedScraperImplBurst {
	private static final Logger logger = LoggerFactory.getLogger(FeedScraperImplBurst.class);

	@Autowired
	FeedScraper feedScraper;

	@Autowired
	Props props;

	@Scheduled(
		fixedRateString = "${jasper.scrape-interval-min}",
		initialDelayString = "${jasper.scrape-delay-min}",
		timeUnit = TimeUnit.MINUTES)
	public void burstScrape() {
		for (var origin : props.getScrapeOrigins()) {
			logger.info("Scraping all {} feeds in a burst.", origin);
			while (feedScraper.scrapeOrigin(origin));
			logger.info("All {} feeds up to date.", origin);
		}
	}

}
