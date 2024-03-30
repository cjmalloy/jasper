package jasper.component.scheduler;

import jasper.component.ConfigCache;
import jasper.component.FeedScraper;
import jasper.config.Config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

import static jasper.domain.proj.HasOrigin.formatOrigin;

@Profile("feed-schedule")
@Component
public class FeedScraperImplSchedule {
	private static final Logger logger = LoggerFactory.getLogger(FeedScraperImplSchedule.class);

	@Autowired
    FeedScraper feedScraper;

	@Autowired
	ConfigCache configs;

	ServerConfig root() {
		return configs.getTemplate("_config/server", "",  ServerConfig.class);
	}

	@Scheduled(
		fixedRateString = "${jasper.scrape-interval-min}",
		initialDelayString = "${jasper.scrape-delay-min}",
		timeUnit = TimeUnit.MINUTES)
	public void scheduleScrape() {
		for (var origin : root().getScrapeOrigins()) {
			logger.info("Scraping all {} feeds on schedule", formatOrigin(origin));
			if (!feedScraper.scrapeOrigin(origin)) {
				logger.info("All {} feeds up to date.", formatOrigin(origin));
			}
		}
	}

}
