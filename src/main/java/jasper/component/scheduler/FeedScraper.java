package jasper.component.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.io.FeedException;
import jasper.component.ConfigCache;
import jasper.component.RssParser;
import jasper.plugin.Feed;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static jasper.domain.proj.HasOrigin.formatOrigin;

@Component
public class FeedScraper {
	private static final Logger logger = LoggerFactory.getLogger(FeedScraper.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	RssParser rssParser;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ConfigCache configs;

	@Scheduled(
		fixedRateString = "${jasper.scrape-interval-min}",
		initialDelayString = "${jasper.scrape-delay-min}",
		timeUnit = TimeUnit.MINUTES)
	public void scheduleScrape() {
		var root = configs.root();
		for (var origin : root.getScrapeOrigins()) {
			logger.info("Scraping {} {} RSS feeds", root.getScrapeBatchSize(), formatOrigin(origin));
			for (var i = 0; i < root.getScrapeBatchSize(); i++) {
				if (!scrapeOrigin(origin)) {
					logger.info("All {} RSS feeds up to date.", formatOrigin(origin));
					return;
				}
			}
			logger.info("Finished scraping {} RSS feeds.", formatOrigin(origin));
		}
	}

	/**
	 * Scrape a feed.
	 * @param origin to search for out of date feeds
	 * @return <code>true</code> if a feed was scraped
	 */
	public boolean scrapeOrigin(String origin) {
		var maybeFeed = refRepository.oldestNeedsScrapeByOrigin(origin);
		if (maybeFeed.isEmpty()) return false;
		var feed = maybeFeed.get();
		var config = feed.getPlugin("+plugin/feed", Feed.class);
		var minutesOld = config.getLastScrape() == null ? 0 : Duration.between(config.getLastScrape(), Instant.now()).toMinutes();
		try {
			logger.info("Scraping {} minute old {} feed: {}.", minutesOld, feed.getTitle(), feed.getUrl());
			rssParser.scrape(feed);
			logger.info("Finished scraping feed: {}.", feed.getUrl());
		} catch (IOException e) {
			logger.error("Error loading feed.", e);
		} catch (FeedException e) {
			logger.error("Error parsing feed.", e);
		} catch (Throwable e) {
			logger.error("Unexpected error scraping feed.", e);
		}
		return true;
	}

}
