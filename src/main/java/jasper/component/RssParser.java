package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.modules.itunes.ITunes;
import com.rometools.modules.mediarss.MediaEntryModuleImpl;
import com.rometools.modules.mediarss.MediaModule;
import com.rometools.rome.feed.module.DCModule;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import jasper.domain.Feed;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.repository.FeedRepository;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Component
public class RssParser {
	private static final Logger logger = LoggerFactory.getLogger(RssParser.class);

	@Autowired
	Ingest ingest;

	@Autowired
	FeedRepository feedRepository;

	@Autowired
	ObjectMapper objectMapper;

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
				Map<String, Object> feedImage = null;
				if (syndFeed.getImage() != null) {
					feedImage = Map.of("url", syndFeed.getImage().getUrl());
				}
				for (var entry : syndFeed.getEntries()) {
					try {
						parseEntry(source, tagSet, entry, feedImage);
					} catch (Exception e) {
						logger.error("Error processing entry", e);
					}
				}
			}
		}
	}

	private void parseEntry(Feed source, HashSet<String> tagSet, SyndEntry entry, Map<String, Object> defaultThumbnail) {
		var ref = new Ref();
		var l = entry.getLink();
		ref.setUrl(l);
		ref.setTitle(entry.getTitle());
		ref.setTags(new ArrayList<>(source.getTags()));
		if (entry.getPublishedDate() != null) {
			ref.setPublished(entry.getPublishedDate().toInstant());
		} else if (l.contains("arxiv.org")) {
			var publishDate = l.substring(l.lastIndexOf("/") + 1, l.lastIndexOf("/") + 5);
			var publishYear = publishDate.substring(0, 2);
			if (Integer.parseInt("20" + publishYear) > Year.now().getValue() + 10) {
				publishYear = "19" + publishYear;
			} else {
				publishYear = "20" + publishYear;
			}
			var publishMonth = publishDate.substring(2);
			ref.setPublished(Instant.parse(publishYear + "-" + publishMonth + "-01T00:00:00.00Z"));
		}
		if (source.isScrapeDescription()) {
			String desc = "";
			if (entry.getDescription() != null) {
				desc = entry.getDescription().getValue();
				if (source.isRemoveDescriptionIndent()) {
					desc = desc.replaceAll("(?m)^\\s+", "");
				}
				ref.setComment(desc);
			}
			var dc = (DCModule) entry.getModule(DCModule.URI);
			if (dc.getCreator() != null) {
				if (!desc.isBlank()) desc += "\n\n\n\n";
				desc += dc.getCreator();
				ref.setComment(desc);
			}
		}
		if (!tagSet.isEmpty()) {
			var plugins = new HashMap<String, Object>();
			if (tagSet.contains("plugin/thumbnail")) {
				parseThumbnail(entry, plugins);
				if (!plugins.containsKey("plugin/thumbnail")) {
					if (defaultThumbnail != null) {
						plugins.put("plugin/thumbnail", defaultThumbnail);
					} else {
						ref.getTags().remove("plugin/thumbnail");
					}
				}
			}
			if (tagSet.contains("plugin/audio")) {
				parseAudio(entry, plugins);
				if (!plugins.containsKey("plugin/audio")) {
					ref.getTags().remove("plugin/audio");
				}
			}
			if (tagSet.contains("plugin/video")) {
				parseVideo(entry, plugins);
				if (!plugins.containsKey("plugin/video")) {
					ref.getTags().remove("plugin/video");
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
		var itunes = (ITunes) entry.getModule(ITunes.URI);
		if (itunes != null &&
			itunes.getImageUri() != null) {
			plugins.put("plugin/thumbnail", Map.of("url", itunes.getImageUri()));
			return;
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
				if ("image".equals(c.getMedium()) || "image/jpeg".equals(c.getType()) || "application/octet-stream".equals(c.getType())) {
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

	private void parseAudio(SyndEntry entry, Map<String, Object> plugins) {
		if (entry.getEnclosures() != null) {
			for (var e : entry.getEnclosures()) {
				if ("audio/mpeg".equals(e.getType())) {
					plugins.put("plugin/audio",  Map.of("url", e.getUrl()));
					return;
				}
			}
		}
	}

	private void parseVideo(SyndEntry entry, Map<String, Object> plugins) {
		if (entry.getEnclosures() != null) {
			for (var e : entry.getEnclosures()) {
				if ("video/mp4".equals(e.getType())) {
					plugins.put("plugin/video",  Map.of("url", e.getUrl()));
					return;
				}
			}
		}
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
