package jasper.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdimension.jchronic.Chronic;
import com.mdimension.jchronic.Options;
import com.mdimension.jchronic.tags.Pointer;
import io.micrometer.core.annotation.Timed;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import jasper.component.dto.JsonLd;
import jasper.domain.Ref;
import jasper.errors.AlreadyExistsException;
import jasper.errors.NotFoundException;
import jasper.plugin.Cache;
import jasper.plugin.Scrape;
import jasper.plugin.Video;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import jasper.security.HostCheck;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import static jasper.domain.proj.HasTags.hasMedia;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class WebScraper {
	private static final Logger logger = LoggerFactory.getLogger(WebScraper.class);
	private static final String CACHE = "cache";

	@Autowired
	HostCheck hostCheck;

	@Autowired
	ConfigCache configs;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Ingest ingest;

	@Autowired
	Optional<Storage> storage;

	@Autowired
	Sanitizer sanitizer;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	CloseableHttpClient client;

	@Autowired
	Images images;

	private final BlockingQueue<Tuple2<String, String>> scrapeLater = new LinkedBlockingQueue<>();
	private final Set<Tuple2<String, String>> scraping = new HashSet<>();

	@Timed(value = "jasper.webscrape")
	public void clearDeleted(String origin) {
		if (storage.isEmpty()) return;
		var deleteLater = new ArrayList<String>();
		storage.get().visitStorage(origin, CACHE, id -> {
			if (!refRepository.cacheExists(id)) deleteLater.add(id);
		});
		deleteLater.forEach(id -> {
			try {
				storage.get().delete(origin, CACHE, id);
			} catch (IOException e) {
				logger.error("Cannot delete file", e);
			}
		});
	}

	@Timed(value = "jasper.webscrape")
	public Ref web(String url, String origin, Scrape config) throws IOException, URISyntaxException {
		var result = new Ref();
		result.setUrl(url);
		String data;
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (storage.isPresent() && maybeRef.isPresent()) {
			result = maybeRef.get();
			var cache = result.getPlugin("_plugin/cache", Cache.class);
			if (cache == null || isBlank(cache.getId())) {
				cache = fetch(result.getUrl(), result.getOrigin());
				if (cache == null) return result;
				result.setPlugin("_plugin/cache", cache);
			}
			data = new String(storage.get().get(origin, CACHE, cache.getId()));
		} else {
			try (var res = doScrape(url)) {
				if (res == null) return result;
				data = new String(res.getEntity().getContent().readAllBytes());
				EntityUtils.consumeQuietly(res.getEntity());
			}
		}
		if (!data.trim().startsWith("<")) return result;
		var doc = Jsoup.parse(data, url);
		result.setTitle(doc.title());
		fixImages(doc, config);
		parseImages(result, doc, config);
		parseThumbnails(result, doc, config);
		parsePublished(result, doc, config);
		removeSelectors(doc, config);
		removeStyleSelectors(doc, config);
		parseVideos(result, doc, config);
		for (var v : doc.select("video")) {
			if (v.select("source").isEmpty()) v.remove();
		}
		parseAudio(result, doc, config);
		for (var a : doc.select("audio")) {
			if (a.select("source").isEmpty()) a.remove();
		}
		parseOpenGraph(result, doc, config);
		parseOembed(result, doc, config);
		parseLinkedData(result, doc, config);
		parseText(result, doc, config);
		return result;
	}

	private void fixImages(Document doc, Scrape config) {
		var images = doc.select("img");
		for (var image : images) {
			var src = image.absUrl("src");
			var dataSrc = image.absUrl("data-src");
			if (isNotBlank(dataSrc)) {
				image.attr("src", src = dataSrc);
			}
			var dataSrcset = image.absUrl("data-srcset");
			if (isNotBlank(dataSrcset)) {
				image.attr("src", src = getImage(dataSrcset.split(",")[0]));
			}
			if (config.getImageFixRegex() == null) continue;
			for (var query : config.getImageFixRegex()) {
				if (src.matches(query)) {
					image.attr("src", src.replaceAll(query, ""));
					break;
				}
			}
		}
	}

	private void parseImages(Ref result, Document doc, Scrape config) {
		if (config.getImageSelectors() == null) return;
		for (var s : config.getImageSelectors()) {
			var image = doc.select(s).first();
			if (image == null) continue;
			if (image.tagName().equals("a")) {
				var src = image.absUrl("href");
				scrapeAsync(src, result.getOrigin());
				addPluginUrl(result, "plugin/image", getImage(src));
				addThumbnailUrl(result, getThumbnail(src));
			} else if (image.hasAttr("data-srcset")){
				var srcset = image.absUrl("data-srcset").split(",");
				var src = srcset[srcset.length - 1].split(" ")[0];
				scrapeAsync(src, result.getOrigin());
				addPluginUrl(result, "plugin/image", getImage(src));
				addThumbnailUrl(result, getThumbnail(src));
				image.parent().remove();
			} else if (image.hasAttr("srcset")){
				var srcset = image.absUrl("srcset").split(",");
				var src = srcset[srcset.length - 1].split(" ")[0];
				scrapeAsync(src, result.getOrigin());
				addPluginUrl(result, "plugin/image", getImage(src));
				addThumbnailUrl(result, getThumbnail(src));
				image.parent().remove();
			} else if (image.hasAttr("src")){
				var src = image.absUrl("src");
				scrapeAsync(src, result.getOrigin());
				addPluginUrl(result, "plugin/image", getImage(src));
				addThumbnailUrl(result, getThumbnail(src));
				image.parent().remove();
			}
		}
	}

	private void parseThumbnails(Ref result, Document doc, Scrape config) {
		if (config.getThumbnailSelectors() == null) return;
		for (var s : config.getThumbnailSelectors()) {
			for (var thumbnail : doc.select(s)) {
				if (thumbnail.tagName().equals("svg")) {
					addThumbnailUrl(result, svgToUrl(sanitizer.clean(thumbnail.outerHtml(), result.getUrl())));
				} else if (thumbnail.hasAttr("href")) {
					var src = thumbnail.absUrl("href");
					scrapeAsync(src, result.getOrigin());
					addThumbnailUrl(result, getThumbnail(src));
				} else if (thumbnail.hasAttr("data-srcset")){
					var srcset = thumbnail.absUrl("data-srcset").split(",");
					var src = srcset[srcset.length - 1].split(" ")[0];
					scrapeAsync(src, result.getOrigin());
					addThumbnailUrl(result, getThumbnail(src));
				} else if (thumbnail.hasAttr("srcset")){
					var srcset = thumbnail.absUrl("srcset").split(",");
					var src = srcset[srcset.length - 1].split(" ")[0];
					scrapeAsync(src, result.getOrigin());
					addThumbnailUrl(result, getThumbnail(src));
				} else if (thumbnail.hasAttr("src")){
					var src = thumbnail.absUrl("src");
					scrapeAsync(src, result.getOrigin());
					addThumbnailUrl(result, getThumbnail(src));
				}
				thumbnail.parent().remove();
			}
		}
	}

	private static Options opts = new Options(Pointer.PointerType.PAST);;
	private void parsePublished(Ref result, Document doc, Scrape config) {
		if (config.getPublishedSelectors() == null) return;
		for (var s : config.getPublishedSelectors()) {
			var published = doc.select(s).first();
			if (published == null) continue;
			String date = "";
			if (published.tagName().equals("time")) {
				date = published.attr("datetime");
			} else {
				result.setPublished(Instant.ofEpochSecond(Chronic.parse(published.text(), opts).getBegin()));
				return;
			}
			if (isBlank(date)) continue;
			try {
				result.setPublished(Instant.parse(date));
				return;
			} catch (DateTimeParseException ignored) {}
		}
	}

	private void removeSelectors(Document doc, Scrape config) {
		if (config.getRemoveSelectors() == null) return;
		for (var r : config.getRemoveSelectors()) doc.select(r).remove();
	}

	private void removeStyleSelectors(Document doc, Scrape config) {
		if (config.getRemoveStyleSelectors() == null) return;
		for (var r : config.getRemoveStyleSelectors()) doc.select(r).removeAttr("style");
	}

	private void parseVideos(Ref result, Document doc, Scrape config) {
		if (config.getVideoSelectors() == null) return;
		for (var s : config.getVideoSelectors()) {
			for (var video : doc.select(s)) {
				if (video.tagName().equals("div")) {
					var src = video.absUrl("data-stream");
					scrapeAsync(src, result.getOrigin());
					addVideoUrl(result, getVideo(src));
				} else if (video.hasAttr("src")) {
					var src = video.absUrl("src");
					scrapeAsync(src, result.getOrigin());
					addVideoUrl(result, getVideo(src));
					addWeakThumbnail(result, getThumbnail(src));
					video.parent().remove();
				}
			}
		}
	}

	private void parseAudio(Ref result, Document doc, Scrape config) {
		if (config.getAudioSelectors() == null) return;
		for (var s : config.getAudioSelectors()) {
			for (var audio : doc.select(s)) {
				if (audio.hasAttr("src")) {
					var src = audio.absUrl("src");
					scrapeAsync(src, result.getOrigin());
					addPluginUrl(result, "plugin/audio", getVideo(src));
					audio.parent().remove();
				}
			}
		}
	}

	private void parseOpenGraph(Ref result, Document doc, Scrape config) {
		if (!config.isOpenGraph()) return;
		for (var metaAudio : doc.select("meta[property=og:audio]")) {
			if (isBlank(metaAudio.attr("content"))) continue;
			addPluginUrl(result, "plugin/audio", metaAudio.absUrl("content"));
		}
		for (var metaVideo : doc.select("meta[property=og:video]")) {
			var videoUrl = metaVideo.absUrl("content");
			if (isBlank(videoUrl)) continue;
			// TODO: video filetypes
			if (videoUrl.endsWith(".m3u8") || videoUrl.endsWith(".mp4")) {
				addVideoUrl(result, videoUrl);
			} else {
				addPluginUrl(result, "plugin/embed", videoUrl);
			}
		}
		for (var metaImage : doc.select("meta[property=og:image]")) {
			if (isBlank(metaImage.attr("content"))) continue;
			// TODO: In some cases load plugin/image
			addThumbnailUrl(result, metaImage.absUrl("content"));
		}
		var metaTitle = doc.select("meta[property=og:title]").first();
		if (metaTitle != null && isNotBlank(metaTitle.attr("content"))) {
			result.setTitle(metaTitle.attr("content"));
		}
		var metaPublished = doc.select("meta[property=article:published_time]").first();
		if (metaPublished != null && isNotBlank(metaPublished.attr("content"))) {
			result.setPublished(Instant.parse(metaPublished.attr("content")));
		}
		metaPublished = doc.select("meta[property=og:article:published_time]").first();
		if (metaPublished != null && isNotBlank(metaPublished.attr("content"))) {
			result.setPublished(Instant.parse(metaPublished.attr("content")));
		}
		var metaReleased = doc.select("meta[property=og:book:release_date]").first();
		if (metaReleased != null && isNotBlank(metaReleased.attr("content"))) {
			result.setPublished(Instant.parse(metaReleased.attr("content")));
		}
	}

	private void parseOembed(Ref result, Document doc, Scrape config) {
		if (!config.isOembedJson()) return;
		var oembed = doc.select("link[type=application/json+oembed]").first();
		if (oembed != null) {
			var oembedUrl = oembed.absUrl("href");
			// TODO: embedded oembed
		}
	}

	private void parseLinkedData(Ref result, Document doc, Scrape config) {
		if (!config.isLdJson());
		var jsonlds = doc.select("script[type=application/ld+json]");
		for (var jsonld : jsonlds) {
			var json = jsonld.html().trim().replaceAll("\n", " ");
			try {
				var configs = json.startsWith("{") ?
					List.of(objectMapper.readValue(json, JsonLd.class)) :
					objectMapper.readValue(json, new TypeReference<List<JsonLd>>(){});
				for (var c : configs) parseLd(result, c);
			} catch (Exception e) {
				logger.warn("Invalid LD+JSON. {}", e.getMessage());
				logger.debug(json);
			}
		}
	}

	private void parseText(Ref result, Document doc, Scrape config) {
		if (!config.isText()) return;
		if (config.getTextSelectors() != null) {
			for (var s : config.getTextSelectors()) {
				var el = doc.body().select(s).first();
				if (el != null) {
					for (var r : config.getRemoveAfterSelectors()) el.select(r).remove();
					result.setComment(sanitizer.clean(el.html(), result.getUrl()));
					return;
				}
			}
		}
		result.setComment(doc.body()
			.wholeText()
			.trim()
			.replaceAll("\t", "")
			.replaceAll("[\n\r]", "\n\n"));
	}

	@Transactional(readOnly = true)
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public Scrape getConfig(String url, String origin) {
		var providers = configs.getAllConfigs(origin, "+plugin/scrape", Scrape.class);
		for (var c : providers) {
			if (c.getSchemes() == null) continue;
			for (var s : c.getSchemes()) {
				var regex = Pattern.quote(s).replace("*", "\\E.*\\Q");
				if (url.matches(regex)) return c;
			}
		}
		return configs.getConfig("config:scrape-catchall", origin, "+plugin/scrape", Scrape.class);
	}

	private void addWeakThumbnail(Ref ref, String url) {
		if (!ref.getTags().contains("plugin/thumbnail")) addThumbnailUrl(ref, url);
	}

	private void addThumbnailUrl(Ref ref, String url) {
		if (url.endsWith(".com") || url.endsWith(".m3u8")) return;
		// TODO: Fallback if image can't load
		addPluginUrl(ref, "plugin/thumbnail", url);
	}

	private void addVideoUrl(Ref ref, String url) {
		if (isBlank(url)) return;
		if (ref.hasTag("plugin/video") && url.endsWith(".m3u8")
			&& !ref.getPlugin("plugin/video", Video.class).getUrl().endsWith(".m3u8")) return;
		addPluginUrl(ref, "plugin/video", url);
	}

	private void parseLd(Ref result, JsonLd ld) {
		if (isNotBlank(ld.getThumbnailUrl())) addWeakThumbnail(result, ld.getThumbnailUrl());
		if ("NewsArticle".equals(ld.getType())) {
			if (isNotBlank(ld.getDatePublished())) {
				result.setPublished(Instant.parse(ld.getDatePublished()));
			}
		}
		if ("AudioObject".equals(ld.getType())) {
			if (isNotBlank(ld.getEmbedUrl())) {
				addPluginUrl(result, "plugin/embed", ld.getEmbedUrl());
			} else if (isNotBlank(ld.getUrl())) {
				addPluginUrl(result, "plugin/audio", ld.getUrl());
			}
		}
		if (("VideoObject".equals(ld.getType())) || "http://schema.org/VideoObject".equals(ld.getType())) {
			if (isNotBlank(ld.getContentUrl())) {
				addVideoUrl(result, ld.getContentUrl());
				if (isBlank(ld.getThumbnailUrl())) addThumbnailUrl(result, ld.getContentUrl());
			} else if (isNotBlank(ld.getEmbedUrl())) {
				addPluginUrl(result, "plugin/embed", ld.getEmbedUrl());
			}
		}
		if ("SocialMediaPosting".equals(ld.getType())) {
		}
		if ("ImageObject".equals(ld.getType())) {
			if (isNotBlank(ld.getEmbedUrl())) {
				addPluginUrl(result, "plugin/embed", ld.getEmbedUrl());
			} else if (isNotBlank(ld.getContentUrl())) {
				if (isBlank(ld.getThumbnailUrl())) {
					addThumbnailUrl(result, ld.getContentUrl());
				} else if (!hasMedia(result)) {
					addPluginUrl(result, "plugin/image", ld.getContentUrl());
				}
			}
		}
		if (ld.getPublisher() != null) {
			for (var p : ld.getPublisher()) {
				if (p.isTextual()) continue;
				var pub = objectMapper.convertValue(p, JsonLd.class);
				if (pub.getLogo() != null) {
					for (var icon : pub.getLogo().isArray() ? pub.getLogo() : List.of(pub.getLogo())) {
						if (icon.isObject()) {
							if (icon.has("url") && isNotBlank(icon.get("url").asText())) addWeakThumbnail(result, icon.get("url").asText());
						} if (isNotBlank(icon.asText())) {
							addWeakThumbnail(result, icon.asText());
						}
					}
				}
			}
		}
		if (ld.getImage() != null) {
			for (var image : ld.getImage()) {
				if (image.isTextual()) {
					addPluginUrl(result, "plugin/image", image.textValue());
				} else {
					parseLd(result, objectMapper.convertValue(image, JsonLd.class));
				}
			}
		}
		if (ld.getVideo() != null) {
			for (var video : ld.getVideo()) {
				if (video.isTextual()) {
					addPluginUrl(result, "plugin/video", video.textValue());
				} else {
					parseLd(result, objectMapper.convertValue(video, JsonLd.class));
				}
			}
		}
	}

	private String getVideo(String src) {
		return src;
	}

	private String getImage(String src) {
		if (src.contains("/full/max/0/")) return src.replace("/full/max/0/", "/full/!1920,1080/0/");
		if (src.contains("?resize")) return src.substring(0, src.indexOf("?resize"));
		return src;
	}

	private String getThumbnail(String src) {
		if (src.contains("/full/max/0/")) return src.replace("/full/max/0/", "/full/!300,200/0/");
		return src;
	}

	@Timed(value = "jasper.rssscrape")
	public String rss(String url) throws IOException {
		try (var res = doScrape(url)) {
			if (res == null) return null;
			var strData = new String(res.getEntity().getContent().readAllBytes());
			EntityUtils.consumeQuietly(res.getEntity());
			if (!strData.trim().startsWith("<")) return null;
			var doc = Jsoup.parse(strData, url);
			return doc.getElementsByTag("link").stream()
				.filter(t -> t.attr("type").equals("application/rss+xml"))
				.filter(t -> t.hasAttr("href"))
				.map(t -> t.absUrl("href"))
				.findFirst().orElse(null);
		}
	}

	public Cache scrape(String url, String origin) {
		if (isBlank(url) || exists(url, origin)) return null;
		return fetch(url, origin);
	}

	public Cache refresh(String url, String origin) {
		if (isBlank(url)) return null;
		return fetch(url, origin, true);
	}

	private boolean exists(String url, String origin) {
		return refRepository.exists(RefFilter.builder()
			.url(url)
			.origin(origin)
			.query("_plugin/cache:!_plugin/cache/async")
			.build().spec());
	}

	public void scrapeAsync(String url, String origin) {
		// TODO: set source cache ref?
		if (isBlank(url)) return;
		url = fixUrl(url);
		scrapeLater.add(Tuple.of(url, origin));
	}

	@Scheduled(fixedDelay = 300)
	public void drainAsyncScrape() {
		scrapeLater.drainTo(scraping);
		for (var s : scraping) fetch(s._1, s._2, true);
		scraping.clear();
	}

	@Timed(value = "jasper.webscrape")
	public Cache fetch(String url, String origin) {
		return fetch(url, origin, null, false);
	}

	@Timed(value = "jasper.webscrape")
	public Cache fetch(String url, String origin, OutputStream os) {
		return fetch(url, origin, os, false);
	}

	@Timed(value = "jasper.webscrape")
	public Cache fetch(String url, boolean thumbnail, String origin, OutputStream os) throws IOException {
		var fullSize = fetch(url, origin, false);
		if (!thumbnail || fullSize == null || fullSize.isThumbnail()) return fetch(url, origin, os, false);
		var thumbnailId = "t_" + fullSize.getId();
		var thumbnailUrl = "internal:" + thumbnailId;
		if (storage.isPresent()) {
			if (storage.get().exists(origin, CACHE, thumbnailId)) {
				return fetch(thumbnailUrl, origin, os);
			} else {
				var data = images.thumbnail(storage.get().stream(origin, CACHE, fullSize.getId()));
				if (data == null) {
					storage.get().stream(origin, CACHE, fullSize.getId(), os);
					// Set this as a thumbnail to disable future attempts
					Ref ref = refRepository.findOneByUrlAndOrigin(url, origin)
						.orElseThrow(() -> new NotFoundException("Ref deleted while fetching"));
					fullSize.setThumbnail(true);
					ref.removeTag("_plugin/cache/async");
					ref.setPlugin("_plugin/cache", fullSize);
					// TODO: Better errors
					ref.addTag("_plugin/error/thumbnail");
					ingest.update(ref, false);
					return fullSize;
				}
				storage.get().storeAt(origin, CACHE, thumbnailId, data);
				if (os != null) StreamUtils.copy(data, os);
				var cache = Cache.builder()
					.id(thumbnailId)
					.thumbnail(true)
					.mimeType("image/png")
					.contentLength((long) data.length)
					.build();
				ingest.create(from(thumbnailUrl, origin, cache, "plugin/thumbnail"), false);
				return cache;
			}
		} else {
			var cache = Cache.builder()
				.id(thumbnailId)
				.thumbnail(true)
				.mimeType("image/png")
				.build();
			try {
				ingest.create(from(thumbnailUrl, origin, cache, "_plugin/cache/async", "plugin/thumbnail"), false);
			} catch (AlreadyExistsException e) {
				// Already creating
			}
			return cache;
		}
	}

	@Timed(value = "jasper.webscrape")
	public Cache fetch(String url, String origin, boolean refresh) {
		return fetch(url, origin, null, refresh);
	}

	@Timed(value = "jasper.webscrape")
	public Cache fetch(String url, String origin, OutputStream os, boolean refresh) {
		if (storage.isEmpty() && os != null) throw new NotImplementedException("Storage is not enabled");
		Ref ref = refRepository.findOneByUrlAndOrigin(url, origin).orElse(null);
		Cache existingCache = null;
		if (ref != null) {
			if (ref.getTags() == null) {
				logger.error("null tags");
				ingest.delete(url, origin);
				return null;
			}
			if (ref.hasTag("_plugin/cache")) {
				existingCache = ref.getPlugin("_plugin/cache", Cache.class);
				if (existingCache.isBan()) return existingCache;
				if (!refresh && !existingCache.isNoStore()) {
					if (isBlank(existingCache.getId())) {
						// If id is blank the last scrape must have failed
						// Wait for the user to manually refresh
						return existingCache;
					}
					if (os != null) storage.get().stream(origin, CACHE, existingCache.getId(), os);
					return existingCache;
				}
			}
			ref.addTag("_plugin/cache");
		}
		if (!url.startsWith("http:") && !url.startsWith("https:")) return existingCache;
		List<String> scrapeMore = List.of();
		try (var res = doScrape(url)) {
			if (res == null) return existingCache;
			var mimeType = res.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue();
			var eTag = res.getFirstHeader(HttpHeaders.ETAG);
			var contentLength = res.getEntity().getContentLength();
			var cos = (CountingOutputStream) (os != null && contentLength <= 0 ? os = new CountingOutputStream(os) : null);
			if (existingCache != null && existingCache.isNoStore()) {
				if (os != null) StreamUtils.copy(res.getEntity().getContent(), os);
				if (cos != null) contentLength = cos.getByteCount();
				EntityUtils.consume(res.getEntity());
				return Cache.builder()
					.id((eTag == null || isBlank(eTag.getValue())) ? "nostore_" + UUID.randomUUID() : eTag.getValue())
					.mimeType(mimeType)
					.contentLength(contentLength <= 0 ? null : contentLength)
					.build();
			} else {
				var id = storage.isPresent() ? storage.get().store(origin, CACHE, res.getEntity().getContent()) : "";
				EntityUtils.consume(res.getEntity());
				if (os != null) storage.get().stream(origin, CACHE, id, os);
				if (cos != null) contentLength = cos.getByteCount();
				var cache = Cache.builder()
					.id(id)
					.mimeType(mimeType)
					.contentLength(storage.isPresent() && contentLength <= 0 ? storage.get().size(origin, CACHE, id) : contentLength)
					.build();
				scrapeMore = createArchive(url, origin, cache);
				if (ref == null) {
					ingest.create(from(url, origin, cache, storage.isEmpty() ? "_plugin/cache/async" : null), false);
				} else {
					if (refresh && existingCache != null && isNotBlank(existingCache.getId())) {
						storage.get().delete(origin, CACHE, existingCache.getId());
						ref.removeTag("_plugin/cache/async");
					} else if (storage.isEmpty()) {
						ref.addTag("_plugin/cache/async");
					}
					ref.setPlugin("_plugin/cache", cache);
				}
				return cache;
			}
		} catch (Exception e) {
			logger.warn("Error fetching", e);
			return existingCache;
		} finally {
			for (var m : scrapeMore) scrapeAsync(m, origin);
			if (ref != null) ingest.update(ref, false);
		}
	}

	@Timed(value = "jasper.webscrape")
	public CloseableHttpResponse doScrape(String url) throws IOException {
		HttpUriRequest request = new HttpGet(url);
		if (!hostCheck.validHost(request.getURI())) {
			logger.info("Invalid host {}", request.getURI().getHost());
			return null;
		}
		request.setHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36");
		var res = client.execute(request);
		if (res.getStatusLine().getStatusCode() == 301 || res.getStatusLine().getStatusCode() == 304) {
			return doScrape(res.getFirstHeader("Location").getElements()[0].getValue());
		}
		return res;
	}

	private List<String> createArchive(String url, String origin, Cache cache) {
		var moreScrape = new ArrayList<String>();
		if (storage.isEmpty() || isBlank(cache.getId())) return moreScrape;
		// M3U8 Manifest
		var data = new String(storage.get().get(origin, CACHE, cache.getId()));
		if (data.trim().startsWith("#") && (url.endsWith(".m3u8") || cache.getMimeType().equals("application/x-mpegURL") || cache.getMimeType().equals("application/vnd.apple.mpegurl"))) {
			try {
				var urlObj = new URL(url);
				var hostPath = urlObj.getProtocol() + "://" + urlObj.getHost() + Path.of(urlObj.getPath()).getParent().toString();
				// TODO: Set archive base URL
				var basePath = "/api/v1/scrape/fetch?url=";
				var buffer = new StringBuilder();
				for (String line : data.split("\n")) {
					if (line.startsWith("#")) {
						buffer.append(line).append("\n");
					} else {
						if (!line.startsWith("http") && !line.startsWith("#")) {
							line = hostPath + "/" + line;
						}
						moreScrape.add(line);
						buffer.append(basePath).append(URLEncoder.encode(line, StandardCharsets.UTF_8)).append("\n");
					}
				}
				storage.get().overwrite(origin, CACHE, cache.getId(), buffer.toString().getBytes());
			} catch (Exception e) {}
		}
		return moreScrape;
	}

	@Timed(value = "jasper.webscrape")
	public Cache cache(String origin, byte[] data, String mimeType, String user) throws IOException {
		if (storage.isEmpty()) throw new NotImplementedException("Storage is not enabled");
		var id = storage.get().store(origin, CACHE, data);
		var cache = Cache.builder()
			.id(id)
			.mimeType(mimeType)
			.contentLength((long) data.length)
			.build();
		ingest.create(from("internal:" + id, origin, cache, user), false);
		return cache;
	}

	@Timed(value = "jasper.webscrape")
	public Cache cache(String origin, InputStream in, String mimeType, String user) throws IOException {
		if (storage.isEmpty()) throw new NotImplementedException("Storage is not enabled");
		var id = storage.get().store(origin, CACHE, in);
		var cache = Cache.builder()
			.id(id)
			.mimeType(mimeType)
			.contentLength(storage.get().size(origin, CACHE, id))
			.build();
		ingest.create(from("internal:" + id, origin, cache, user), false);
		return cache;
	}

	private String fixUrl(String url) {
		// TODO: Add plugin to override like oembeds
//		return url.replaceAll("%20", "+");
		return url.replaceAll(" ", "%20");
	}

	private String svgToUrl(String svg) {
		return "data:image/svg+xml," + svg
			.replaceAll("<svg", svg.contains("xmlns") ? "<svg" : "<svg xmlns='http://www.w3.org/2000/svg'")
			.replaceAll("viewbox", "viewBox")
			.replaceAll("\"", "'")
			.replaceAll("%", "%25")
			.replaceAll("#", "%23")
			.replaceAll("\\{", "%7B")
			.replaceAll("}", "%7D")
			.replaceAll("<", "%3C")
			.replaceAll(">", "%3E")
			.replaceAll("\s+"," ");
	}

	private void addPluginUrl(Ref ref, String tag, String url) {
		scrapeAsync(url, ref.getOrigin());
		ref.setPlugin(tag, Map.of("url", url));
	}

	public static Ref from(String url, String origin, Cache cache, String ...tags) {
		return from(url, origin, tags).setPlugin("_plugin/cache", cache);
	}

	public static Ref from(String url, String origin, String ...tags) {
		var result = new Ref();
		result.setUrl(url);
		result.setOrigin(origin);
		result.addTag("_plugin/cache");
		result.addTag("internal");
		for (var tag : tags) result.addTag(tag);
		return result;
	}

}
