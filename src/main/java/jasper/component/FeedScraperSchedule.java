package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.io.FeedException;
import jasper.plugin.Feed;
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

@Profile("feed-schedule")
@Component
public class FeedScraperSchedule {
	private static final Logger logger = LoggerFactory.getLogger(FeedScraperSchedule.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	RssParser rssParser;

	@Autowired
	ObjectMapper objectMapper;

	@Scheduled(
		fixedRateString = "${jasper.scrape-interval-min}",
		initialDelayString = "${jasper.scrape-delay-min}",
		timeUnit = TimeUnit.MINUTES)
	public void scheduleScrape() {
		logger.info("Scraping all feeds on schedule.");
		var maybeFeed = refRepository.oldestNeedsScrapeByOrigin("");
		if (maybeFeed.isEmpty()) {
			logger.info("All feeds up to date.");
			return;
		}
		var ref = maybeFeed.get();
		var feed = objectMapper.convertValue(ref.getPlugins().get("+plugin/feed"), Feed.class);
		var minutesOld = Duration.between(feed.getLastScrape(), Instant.now()).toMinutes();
		try {
			logger.info("Scraping {} minute old {} feed: {}.", minutesOld, ref.getTitle(), ref.getUrl());
			rssParser.scrape(ref);
			logger.info("Finished scraping feed: {}.", ref.getUrl());
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
