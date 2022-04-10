package ca.hc.jasper.component;

import java.io.IOException;
import java.net.URL;

import ca.hc.jasper.domain.Feed;
import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.errors.AlreadyExistsException;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FeedScraper {
	private static final Logger logger = LoggerFactory.getLogger(FeedScraper.class);

	@Autowired
	Ingest ingest;

	public void scrape(Feed source) throws IOException, FeedException {
		SyndFeedInput input = new SyndFeedInput();
		SyndFeed result = input.build(new XmlReader(new URL(source.getUrl())));
		for (var entryObj : result.getEntries()) {
			SyndEntry entry = (SyndEntry) entryObj;
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
