package jasper.component;

import com.rometools.rome.io.FeedException;
import jasper.repository.FeedRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Profile("feed-burst")
@Component
public class FeedScraperBurst {
	private static final Logger logger = LoggerFactory.getLogger(FeedScraperBurst.class);

	@Autowired
	Ingest ingest;

	@Autowired
	FeedRepository feedRepository;

	@Autowired
	RssParser rssParser;

	@Scheduled(
		fixedRateString = "${application.scrape-interval-min}",
		initialDelayString = "${application.scrape-delay-min}",
		timeUnit = TimeUnit.MINUTES)
	public void burstScrape() {
		logger.info("Scraping all feeds in a burst.");
		while (true) {
			var maybeFeed = feedRepository.oldestNeedsScrapeByOrigin("");
			if (maybeFeed.isEmpty()) {
				logger.info("All feeds up to date.");
				return;
			}
			var feed = maybeFeed.get();
			var minutesOld = feed.getLastScrape() == null ? 0 : Duration.between(feed.getLastScrape(), Instant.now()).toMinutes();
			try {
				rssParser.scrape(feed);
				logger.info("Finished scraping {} minute old {} feed: {}.", minutesOld, feed.getName(), feed.getUrl());
			} catch (IOException e) {
				e.printStackTrace();
				logger.error("Error loading feed.");
			} catch (FeedException e) {
				e.printStackTrace();
				logger.error("Error parsing feed.");
			} catch (Throwable e) {
				e.printStackTrace();
				logger.error("Unexpected error scraping feed.");
			}
		}
	}

}
