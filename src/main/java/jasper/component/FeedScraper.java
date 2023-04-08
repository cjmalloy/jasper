package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.io.FeedException;
import jasper.plugin.Feed;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
public class FeedScraper {
	private static final Logger logger = LoggerFactory.getLogger(FeedScraper.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	RssParser rssParser;

	@Autowired
	ObjectMapper objectMapper;

	/**
	 * Scrape a feed.
	 * @param origin to search for out of date feeds
	 * @return <code>true</code> if a feed was scraped
	 */
	public boolean scrapeOrigin(String origin) {
		var maybeFeed = refRepository.oldestNeedsScrapeByOrigin(origin);
		if (maybeFeed.isEmpty()) return false;
		var feed = maybeFeed.get();
		var config = objectMapper.convertValue(feed.getPlugins().get("+plugin/feed"), Feed.class);
		var minutesOld = config.getLastScrape() == null ? 0 : Duration.between(config.getLastScrape(), Instant.now()).toMinutes();
		try {
			logger.info("Scraping {} minute old {} feed: {}.", minutesOld, feed.getTitle(), feed.getUrl());
			rssParser.scrape(feed);
			logger.info("Finished scraping feed: {}.", feed.getUrl());
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
		return true;
	}

}
