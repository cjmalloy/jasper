package jasper.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.modules.itunes.ITunes;
import com.rometools.modules.mediarss.MediaEntryModuleImpl;
import com.rometools.modules.mediarss.MediaModule;
import com.rometools.rome.feed.module.DCModule;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import io.micrometer.core.annotation.Timed;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.plugin.Feed;
import jasper.repository.RefRepository;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Component
public class RssParser {
	private static final Logger logger = LoggerFactory.getLogger(RssParser.class);

	@Autowired
	Ingest ingest;

	@Autowired
	RefRepository refRepository;

	@Autowired
	ObjectMapper objectMapper;

	@Timed("jasper.feed")
	public void scrape(Ref feed) throws IOException, FeedException {
		var config = objectMapper.convertValue(feed.getPlugins().get("+plugin/feed"), Feed.class);
		var lastScrape = config.getLastScrape();
		config.setLastScrape(Instant.now());
		saveConfig(feed, config);

		var tagSet = new HashSet<String>();
		if (config.getAddTags() != null) tagSet.addAll(config.getAddTags());

		int timeout = 30 * 1000; // 30 seconds
		RequestConfig requestConfig = RequestConfig
			.custom()
			.setConnectTimeout(timeout)
			.setConnectionRequestTimeout(timeout)
			.setSocketTimeout(timeout).build();
		var builder = HttpClients.custom().setDefaultRequestConfig(requestConfig);
		try (CloseableHttpClient client = builder.build()) {
			HttpUriRequest request = new HttpGet(feed.getUrl());
			if (!config.isDisableEtag() && config.getEtag() != null) {
				request.setHeader(HttpHeaders.IF_NONE_MATCH, config.getEtag());
			}
			if (lastScrape != null) {
				request.setHeader(HttpHeaders.IF_MODIFIED_SINCE, DateTimeFormatter.RFC_1123_DATE_TIME.format(lastScrape.atZone(ZoneId.of("GMT"))));
			}
			request.setHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36");
			try (CloseableHttpResponse response = client.execute(request)) {
				if (response.getStatusLine().getStatusCode() == 304) {
					logger.info("Feed {} not modified since {}",
						feed.getTitle(), lastScrape);
					return;
				}
				try (InputStream stream = response.getEntity().getContent()) {
					if (!config.isDisableEtag()) {
						var etag = response.getFirstHeader(HttpHeaders.ETAG);
						if (etag != null) {
							config.setEtag(etag.getValue());
							saveConfig(feed, config);
						} else if (config.getEtag() != null) {
							config.setEtag(null);
							saveConfig(feed, config);
						}
					}
					SyndFeedInput input = new SyndFeedInput();
					SyndFeed syndFeed = input.build(new XmlReader(stream));
					Map<String, Object> feedImage = null;
					if (syndFeed.getImage() != null) {
						feedImage = Map.of("url", syndFeed.getImage().getUrl());
					}
					for (var entry : syndFeed.getEntries()) {
						Ref ref;
						try {
							ref = parseEntry(feed, config, tagSet, entry, feedImage);
						} catch (Exception e) {
							logger.error("Error processing entry", e);
							continue;
						}
						if (ref.getPublished().isBefore(feed.getPublished())) {
							logger.debug("Skipping RSS entry in feed {} which was published before feed publish date. {} {}",
								feed.getTitle(), ref.getTitle(), ref.getUrl());
							continue;
						}
						ref.setOrigin(config.getOrigin());
						try {
							ingest.ingest(ref);
						} catch (AlreadyExistsException e) {
							logger.debug("Skipping RSS entry in feed {} which already exists. {} {}",
								feed.getTitle(), ref.getTitle(), ref.getUrl());
						}
					}
				}
			}
		}
	}

	private void saveConfig(Ref feed, Feed config) {
		feed.getPlugins().set("+plugin/feed", objectMapper.convertValue(config, JsonNode.class));
		refRepository.save(feed);
	}

	private Ref parseEntry(Ref feed, Feed config, HashSet<String> tagSet, SyndEntry entry, Map<String, Object> defaultThumbnail) {
		var ref = new Ref();
		var l = entry.getLink();
		ref.setUrl(l);
		ref.setTitle(entry.getTitle());
		ref.setSources(List.of(feed.getUrl()));
		ref.setTags(new ArrayList<>(config.getAddTags()));
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
		var comment = "";
		if (config.isScrapeDescription() || config.isScrapeContents()) {
			SyndContent desc = null;
			if (config.isScrapeContents() && !entry.getContents().isEmpty()) {
				desc = getPreferredType(entry.getContents());
			} else if (config.isScrapeDescription() && entry.getDescription() != null) {
				desc = entry.getDescription();
			}
			if (desc != null) {
				comment = desc.getValue();
				if (isHtml(desc)) {
					comment = comment.replaceAll("(?m)^\\s+", "");
				}
			}
		}
		var dc = (DCModule) entry.getModule(DCModule.URI);
		if (config.isScrapeAuthor() && !dc.getCreators().isEmpty()) {
			if (!comment.isBlank()) comment += "\n\n\n\n";
			comment += String.join(", ", dc.getCreators());
		}
		ref.setComment(comment);
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
		return ref;
	}

	private SyndContent getPreferredType(List<SyndContent> contents) {
		for (var c : contents) if (isHtml(c)) return c;
		for (var c : contents) if (isText(c)) return c;
		return contents.get(0);
	}

	private boolean isHtml(SyndContent content) {
		var t = content.getType();
		return t != null && (t.equals("text/html") || t.equals("html"));
	}

	private boolean isText(SyndContent content) {
		var t = content.getType();
		// When used for the description of an entry, if null 'text/plain' must be assumed.
		return t == null || t.equals("text/plain");
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
			plugins.put("plugin/thumbnail", Map.of("url", media.getMetadata().getThumbnail()[0].getUrl()));
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
		plugins.put("plugin/thumbnail", Map.of("url", group.getMetadata().getThumbnail()[0].getUrl()));
	}

	private void parseAudio(SyndEntry entry, Map<String, Object> plugins) {
		if (entry.getEnclosures() != null) {
			for (var e : entry.getEnclosures()) {
				if (e.getType() == null) continue;
				if (e.getType().startsWith("audio/")) {
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
