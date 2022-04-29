package jasper.component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.modules.mediarss.MediaEntryModuleImpl;
import com.rometools.modules.mediarss.MediaModule;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.*;
import jasper.domain.Feed;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.repository.FeedRepository;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
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

	@Autowired
	ObjectMapper objectMapper;

	@Scheduled(fixedRate = 1, initialDelayString = "${application.scrape-delay-min}", timeUnit = TimeUnit.MINUTES)
	public void scheduleScrape() {
		logger.info("Scraping all feeds on schedule.");
		var maybeFeed = feedRepository.oldestNeedsScrape();
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

	public void scrape(Feed source) throws IOException, FeedException {
		source.setLastScrape(Instant.now());
		feedRepository.save(source);

		var tagSet = new HashSet<String>();
		if (source.getTags() != null) tagSet.addAll(source.getTags());

		int timeout = 30 * 1000; // 30 seconds
		RequestConfig requestConfig = RequestConfig
			.custom()
			.setConnectTimeout(timeout)
			.setConnectionRequestTimeout(timeout)
			.setSocketTimeout(timeout).build();
		var builder = HttpClients.custom().setDefaultRequestConfig(requestConfig);
		try (CloseableHttpClient client = builder.build()) {
			HttpUriRequest request = new HttpGet(source.getUrl());
			request.setHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36");
			try (CloseableHttpResponse response = client.execute(request);
				InputStream stream = response.getEntity().getContent()) {
				SyndFeedInput input = new SyndFeedInput();
				SyndFeed syndFeed = input.build(new XmlReader(stream));
				for (var entry : syndFeed.getEntries()) {
					parseEntry(source, tagSet, entry);
				}
			}
		}
	}

	private void parseEntry(Feed source, HashSet<String> tagSet, SyndEntry entry) {
		var ref = new Ref();
		ref.setUrl(entry.getLink());
		ref.setTitle(entry.getTitle());
		ref.setTags(new ArrayList<>(source.getTags()));
		ref.setPublished(entry.getPublishedDate().toInstant());
		if (source.isScrapeDescription() && entry.getDescription() != null) {
			String desc = entry.getDescription().getValue();
			if (source.isRemoveDescriptionIndent()) {
				desc = desc.replaceAll("(?m)^\\s+", "");
			}
			ref.setComment(desc);
		}
		if (!tagSet.isEmpty()) {
			var plugins = new HashMap<String, Object>();
			if (tagSet.contains("plugin/thumbnail")) {
				parseThumbnail(entry, plugins);
				if (!plugins.containsKey("plugin/thumbnail")) {
					ref.getTags().remove("plugin/thumbnail");
				}
			}
			if (tagSet.contains("plugin/embed")) parseEmbed(entry, plugins);
			ref.setPlugins(objectMapper.valueToTree(plugins));
		}
		try {
			ingest.ingest(ref);
		} catch (AlreadyExistsException e) {
			logger.debug("Skipping RSS entry in feed {} which already exists. {} {}",
				source.getName(), ref.getTitle(), ref.getUrl());
		}
	}

	private void parseThumbnail(SyndEntry entry, Map<String, Object> plugins) {
		if (entry.getEnclosures() != null) {
			for (var e : entry.getEnclosures()) {
				if ("image/jpg".equals(e.getType()) || "image/png".equals(e.getType())) {
					plugins.put("plugin/thumbnail",  Map.of("url", e.getUrl()));
					return;
				}
			}
		}
		var media = (MediaEntryModuleImpl) entry.getModule(MediaModule.URI);
		if (media == null) return;
		if (media.getMetadata() != null &&
			media.getMetadata().getThumbnail() != null &&
			media.getMetadata().getThumbnail().length != 0) {
			plugins.put("plugin/thumbnail", media.getMetadata().getThumbnail()[0]);
			return;
		}
		if (media.getMetadata() != null &&
			media.getMediaContents() != null) {
			for (var c : media.getMediaContents()) {
				if ("image".equals(c.getMedium())) {
					plugins.put("plugin/thumbnail", c.getReference());
					return;
				}
			}
		}
		if (media.getMediaGroups().length == 0) return;
		var group = media.getMediaGroups()[0];
		if (group.getMetadata().getThumbnail().length == 0) return;
		plugins.put("plugin/thumbnail", group.getMetadata().getThumbnail()[0]);
	}

	private void parseEmbed(SyndEntry entry, Map<String, Object> plugins) {
		var youtubeEmbed = entry
			.getForeignMarkup()
			.stream()
			.filter(e -> "videoId".equals(e.getName()))
			.filter(e -> "yt".equals(e.getNamespacePrefix()))
			.findFirst();
		if (youtubeEmbed.isPresent()) {
			plugins.put("plugin/embed", Map.of("url", "https://www.youtube.com/embed/" + youtubeEmbed.get().getValue()));
			return;
		}

		var media = (MediaEntryModuleImpl) entry.getModule(MediaModule.URI);
		if (media == null) return;
		if (media.getMetadata() == null) return;
		if (media.getMetadata().getEmbed() == null) return;
		plugins.put("plugin/embed", media.getMetadata().getEmbed());
	}
}
