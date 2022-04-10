package ca.hc.jasper.component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import ca.hc.jasper.domain.Feed;
import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.errors.AlreadyExistsException;
import ca.hc.jasper.repository.FeedRepository;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.*;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FeedScraper {
	private static final Logger logger = LoggerFactory.getLogger(FeedScraper.class);

	@Autowired
	Ingest ingest;

	@Autowired
	FeedRepository feedRepository;

	public void scrape(Feed source) throws IOException, FeedException {
		source.setLastScrape(Instant.now());
		feedRepository.save(source);

		try (CloseableHttpClient client = HttpClients.createMinimal()) {
			HttpUriRequest request = new HttpGet(source.getUrl());;
			request.setHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36");
			try (CloseableHttpResponse response = client.execute(request);
				InputStream stream = response.getEntity().getContent()) {
				SyndFeedInput input = new SyndFeedInput();
				SyndFeed syndFeed = input.build(new XmlReader(stream));
				for (var entry : syndFeed.getEntries()) {
					var ref = new Ref();
					ref.setUrl(entry.getLink());
					ref.setTitle(entry.getTitle());
					ref.setTags(source.getTags());
					ref.setPublished(entry.getPublishedDate().toInstant());
					if (entry.getDescription() != null) {
						ref.setComment(entry.getDescription().getValue());
					}
					try {
						ingest.ingest(ref);
					} catch (AlreadyExistsException e) {
						logger.info("Skipping RSS entry in feed {} which already exists. {} {}",
							source.getName(), ref.getTitle(), ref.getUrl());
					}
				}
			}
		}
	}

	@Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
	public void scheduleScrape() {
		logger.info("Scraping all feeds on schedule.");
		var fourMinAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
		var maybeFeed = feedRepository.findFirstByLastScrapeBeforeOrLastScrapeIsNullOrderByLastScrapeAsc(fourMinAgo);
		if (maybeFeed.isEmpty()) {
			logger.info("All feeds up to date.");
			return;
		}
		var feed = maybeFeed.get();
		try {
			scrape(feed);
			logger.info("Finished scraping {} feed: {}.", feed.getName(), feed.getUrl());
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
