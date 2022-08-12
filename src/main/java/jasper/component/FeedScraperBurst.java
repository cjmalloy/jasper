package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.io.FeedException;
import jasper.domain.plugin.Feed;
import jasper.repository.RefRepository;
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
	RefRepository refRepository;

	@Autowired
	RssParser rssParser;

	@Autowired
	ObjectMapper objectMapper;

	@Scheduled(
		fixedRateString = "${application.scrape-interval-min}",
		initialDelayString = "${application.scrape-delay-min}",
		timeUnit = TimeUnit.MINUTES)
	public void burstScrape() {
		logger.info("Scraping all feeds in a burst.");
		while (true) {
			var maybeFeed = refRepository.oldestNeedsScrapeByOrigin("");
			if (maybeFeed.isEmpty()) {
				logger.info("All feeds up to date.");
				return;
			}
			var feed = maybeFeed.get();
			var config = objectMapper.convertValue(feed.getPlugins().get("+plugin/feed"), Feed.class);
			var minutesOld = config.getLastScrape() == null ? 0 : Duration.between(config.getLastScrape(), Instant.now()).toMinutes();
			try {
				rssParser.scrape(feed);
				logger.info("Finished scraping {} minute old {} feed: {}.", minutesOld, feed.getTitle(), feed.getUrl());
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
