package jasper.component;

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
import jasper.errors.OperationForbiddenOnOriginException;
import jasper.plugin.Audio;
import jasper.plugin.Config;
import jasper.plugin.Embed;
import jasper.plugin.Feed;
import jasper.plugin.Thumbnail;
import jasper.plugin.Video;
import jasper.repository.RefRepository;
import jasper.security.HostCheck;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static jasper.domain.proj.HasOrigin.origin;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class RssParser {
	private static final Logger logger = LoggerFactory.getLogger(RssParser.class);

	@Autowired
	HostCheck hostCheck;

	@Autowired
	Ingest ingest;

	@Autowired
	Sanitizer sanitizer;

	@Autowired
	WebScraper webScraper;

	@Autowired
	RefRepository refRepository;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	ConfigCache configs;

	Config root() {
		return configs.getTemplate("_config", "", Config.class);
	}

	@Timed("jasper.feed")
	public void scrape(Ref feed) throws IOException, FeedException {
		var root = root();
		if (!root.getScrapeOrigins().contains(origin(feed.getOrigin()))) {
			logger.debug("Scrape origins: {}", root.getScrapeOrigins());
			throw new OperationForbiddenOnOriginException(feed.getOrigin());
		}
		var config = feed.getPlugin("+plugin/feed", Feed.class);
		var lastScrape = config.getLastScrape();
		config.setLastScrape(Instant.now());
		feed.setPlugin("+plugin/feed", config);
		refRepository.save(feed);

		int timeout = 30 * 1000; // 30 seconds
		RequestConfig requestConfig = RequestConfig
			.custom()
			.setConnectTimeout(timeout)
			.setConnectionRequestTimeout(timeout)
			.setSocketTimeout(timeout).build();
		var builder = HttpClients.custom().setDefaultRequestConfig(requestConfig);
		try (CloseableHttpClient client = builder.build()) {
			HttpUriRequest request = new HttpGet(feed.getUrl());
			if (!hostCheck.validHost(request.getURI())) {
				logger.info("Invalid host {}", request.getURI().getHost());
				return;
			}

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
							feed.setPlugin("+plugin/feed", config);
							refRepository.save(feed);
						} else if (config.getEtag() != null) {
							config.setEtag(null);
							feed.setPlugin("+plugin/feed", config);
							refRepository.save(feed);
						}
					}
					SyndFeedInput input = new SyndFeedInput();
					SyndFeed syndFeed = input.build(new XmlReader(stream));
					Thumbnail feedImage = null;
					if (syndFeed.getImage() != null) {
						var image = syndFeed.getImage().getUrl();
						webScraper.fetch(image, feed.getOrigin());
						feedImage = Thumbnail.builder().url(image).build();
						if (!feed.getTags().contains("plugin/thumbnail")) {
							feed.setPlugin("plugin/thumbnail", feedImage);
							refRepository.save(feed);
						}
					}
					for (var entry : syndFeed.getEntries()) {
						Ref ref;
						try {
							ref = parseEntry(feed, config, entry, feedImage);
						} catch (AlreadyExistsException e) {
							logger.debug("Skipping RSS entry in feed {} which already exists. {} {}",
								feed.getTitle(), entry.getTitle(), entry.getLink());
							continue;
						} catch (Exception e) {
							logger.error("Error processing entry", e);
							continue;
						}
						if (ref.getPublished().isBefore(feed.getPublished())) {
							logger.warn("RSS entry in feed {} which was published before feed publish date. {} {}",
								feed.getTitle(), ref.getTitle(), ref.getUrl());
							feed.setPublished(ref.getPublished().minus(1, ChronoUnit.DAYS));
							refRepository.save(feed);
						}
						ref.setOrigin(feed.getOrigin());
						try {
							ingest.create(ref, false);
						} catch (AlreadyExistsException e) {
							logger.debug("Skipping RSS entry in feed {} which already exists. {} {}",
								feed.getTitle(), ref.getTitle(), ref.getUrl());
						}
					}
				}
			}
		}
	}

	private Ref parseEntry(Ref feed, Feed config, SyndEntry entry, Thumbnail defaultThumbnail) {
		var ref = new Ref();
		var link = entry.getLink();
		if (config.isStripQuery() && link.contains("?")) {
			link = link.substring(0, link.indexOf("?"));
		}
		if (refRepository.existsByUrlAndOrigin(link, feed.getOrigin())) {
			throw new AlreadyExistsException();
		}
		if (config.isScrapeWebpage()) {
			try {
				var scrapeConfig = webScraper.getConfig(link, feed.getOrigin());
				if (scrapeConfig == null) {
					scrapeConfig = webScraper.getDefaultConfig(feed.getOrigin());
				}
				if (scrapeConfig == null) {
					logger.warn("Scrape requested, but no config found.");
				} else {
					var web = webScraper.web(link, feed.getOrigin(), scrapeConfig);
					if (web != null) ref = web;
				}
			} catch (Exception ignored) {}
		}
		ref.setUrl(link);
		ref.setTitle(entry.getTitle());
		ref.setSources(List.of(feed.getUrl()));
		ref.addTags(config.getAddTags());
		if (entry.getPublishedDate() != null) {
			ref.setPublished(entry.getPublishedDate().toInstant());
		} else if (link.contains("arxiv.org")) {
			var publishDate = link.substring(link.lastIndexOf("/") + 1, link.lastIndexOf("/") + 5);
			var publishYear = publishDate.substring(0, 2);
			if (Integer.parseInt("20" + publishYear) > Year.now().getValue() + 10) {
				publishYear = "19" + publishYear;
			} else {
				publishYear = "20" + publishYear;
			}
			var publishMonth = publishDate.substring(2);
			ref.setPublished(Instant.parse(publishYear + "-" + publishMonth + "-01T00:00:00.00Z"));
		} else if (entry.getUpdatedDate() != null) {
			ref.setPublished(entry.getUpdatedDate().toInstant());
		}
		var comment = isNotBlank(ref.getComment()) ? ref.getComment() : "";
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
					comment = sanitizer.clean(comment);
				}
			}
		}
		var dc = (DCModule) entry.getModule(DCModule.URI);
		if (config.isScrapeAuthors() && !dc.getCreators().isEmpty()) {
			if (!comment.isBlank()) comment += "\n\n\n\n";
			comment += String.join(", ", dc.getCreators());
		}
		ref.setComment(comment);
		if (config.isScrapeThumbnail()) {
			parseThumbnail(entry, ref);
			if (!ref.hasPlugin("plugin/thumbnail")) {
				if (defaultThumbnail != null) {
					ref.setPlugin("plugin/thumbnail", defaultThumbnail);
				}
			}
			if (ref.hasPlugin("plugin/thumbnail")) {
				webScraper.scrapeAsync(ref.getPlugin("plugin/thumbnail", Thumbnail.class).getUrl(), feed.getOrigin());
			}
		}
		if (config.isScrapeAudio()) {
			parseAudio(entry, ref);
			if (ref.hasPlugin("plugin/audio")) {
				webScraper.scrapeAsync(ref.getPlugin("plugin/audio", Audio.class).getUrl(), feed.getOrigin());
			}
		}
		if (config.isScrapeVideo()) {
			parseVideo(entry, ref);
			if (ref.hasPlugin("plugin/video")) {
				webScraper.scrapeAsync(ref.getPlugin("plugin/video", Video.class).getUrl(), feed.getOrigin());
			}
		}
		if (config.isScrapeEmbed()) {
			parseEmbed(entry, ref);
			if (ref.hasPlugin("plugin/embed")) {
				webScraper.scrapeAsync(ref.getPlugin("plugin/embed", Embed.class).getUrl(), feed.getOrigin());
			}
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

	private void parseThumbnail(SyndEntry entry, Ref ref) {
		if (entry.getEnclosures() != null) {
			for (var e : entry.getEnclosures()) {
				if ("image/jpg".equals(e.getType()) || "image/png".equals(e.getType())) {
					ref.setPlugin("plugin/thumbnail",  Thumbnail.builder().url(e.getUrl()).build());
					return;
				}
			}
		}
		var itunes = (ITunes) entry.getModule(ITunes.URI);
		if (itunes != null &&
			itunes.getImageUri() != null) {
			ref.setPlugin("plugin/thumbnail", Map.of("url", itunes.getImageUri()));
			return;
		}
		var media = (MediaEntryModuleImpl) entry.getModule(MediaModule.URI);
		if (media == null) return;
		if (media.getMetadata() != null &&
			media.getMetadata().getThumbnail() != null &&
			media.getMetadata().getThumbnail().length != 0) {
			ref.setPlugin("plugin/thumbnail", Map.of("url", media.getMetadata().getThumbnail()[0].getUrl()));
			return;
		}
		if (media.getMetadata() != null &&
			media.getMediaContents() != null) {
			for (var c : media.getMediaContents()) {
				if ("image".equals(c.getMedium()) || "image/jpeg".equals(c.getType()) || "application/octet-stream".equals(c.getType())) {
					ref.setPlugin("plugin/thumbnail", c.getReference());
					return;
				}
			}
		}
		if (media.getMediaGroups().length == 0) return;
		var group = media.getMediaGroups()[0];
		if (group.getMetadata().getThumbnail().length == 0) return;
		ref.setPlugin("plugin/thumbnail", Map.of("url", group.getMetadata().getThumbnail()[0].getUrl()));
	}

	private void parseAudio(SyndEntry entry, Ref ref) {
		if (entry.getEnclosures() != null) {
			for (var e : entry.getEnclosures()) {
				if (e.getType() == null) continue;
				if (e.getType().startsWith("audio/")) {
					ref.setPlugin("plugin/audio",  Map.of("url", e.getUrl()));
					return;
				}
			}
		}
	}

	private void parseVideo(SyndEntry entry, Ref ref) {
		if (entry.getEnclosures() != null) {
			for (var e : entry.getEnclosures()) {
				if ("video/mp4".equals(e.getType())) {
					ref.setPlugin("plugin/video",  Map.of("url", e.getUrl()));
					return;
				}
			}
		}
	}

	private void parseEmbed(SyndEntry entry, Ref ref) {
		var youtubeEmbed = entry
			.getForeignMarkup()
			.stream()
			.filter(e -> "videoId".equals(e.getName()))
			.filter(e -> "yt".equals(e.getNamespacePrefix()))
			.findFirst();
		if (youtubeEmbed.isPresent()) {
			ref.setPlugin("plugin/embed", Map.of("url", "https://www.youtube.com/embed/" + youtubeEmbed.get().getValue()));
			return;
		}

		var media = (MediaEntryModuleImpl) entry.getModule(MediaModule.URI);
		if (media == null) return;
		if (media.getMetadata() == null) return;
		if (media.getMetadata().getEmbed() == null) return;
		ref.setPlugin("plugin/embed", media.getMetadata().getEmbed());
	}
}
