package jasper.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdimension.jchronic.Chronic;
import com.mdimension.jchronic.Options;
import com.mdimension.jchronic.tags.Pointer;
import io.micrometer.core.annotation.Timed;
import jasper.component.dto.JsonLd;
import jasper.domain.Ref;
import jasper.domain.Web;
import jasper.plugin.Scrape;
import jasper.repository.RefRepository;
import jasper.repository.WebRepository;
import jasper.repository.filter.RefFilter;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.databind.node.TextNode.valueOf;
import static jasper.domain.proj.Tag.hasMedia;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class WebScraper {
	private static final Logger logger = LoggerFactory.getLogger(WebScraper.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	WebRepository webRepository;

	@Autowired
	Sanitizer sanitizer;

	@Autowired
	ObjectMapper objectMapper;

	WebScraper self = this;

	private final BlockingQueue<String> scrapeLater = new LinkedBlockingQueue<>();
	private final Set<String> scraping = new HashSet<>();

	@Timed(value = "jasper.webscrape")
	public Ref web(String url, Scrape config) throws IOException, URISyntaxException {
		var result = new Ref();
		result.setUrl(url);
		var web = fetch(url);
		if (web == null || web.getData() == null) return result;
		var strData = new String(web.getData());
		if (!strData.trim().startsWith("<")) return result;
		var doc = Jsoup.parse(strData, url);
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
				self.scrapeAsync(src);
				addPluginUrl(result, "plugin/image", getImage(src));
				addThumbnailUrl(result, getThumbnail(src));
			} else if (image.hasAttr("data-srcset")){
				var srcset = image.absUrl("data-srcset").split(",");
				var src = srcset[srcset.length - 1].split(" ")[0];
				self.scrapeAsync(src);
				addPluginUrl(result, "plugin/image", getImage(src));
				addThumbnailUrl(result, getThumbnail(src));
				image.parent().remove();
			} else if (image.hasAttr("srcset")){
				var srcset = image.absUrl("srcset").split(",");
				var src = srcset[srcset.length - 1].split(" ")[0];
				self.scrapeAsync(src);
				addPluginUrl(result, "plugin/image", getImage(src));
				addThumbnailUrl(result, getThumbnail(src));
				image.parent().remove();
			} else if (image.hasAttr("src")){
				var src = image.absUrl("src");
				self.scrapeAsync(src);
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
					self.scrapeAsync(src);
					addThumbnailUrl(result, getThumbnail(src));
				} else if (thumbnail.hasAttr("data-srcset")){
					var srcset = thumbnail.absUrl("data-srcset").split(",");
					var src = srcset[srcset.length - 1].split(" ")[0];
					self.scrapeAsync(src);
					addThumbnailUrl(result, getThumbnail(src));
				} else if (thumbnail.hasAttr("srcset")){
					var srcset = thumbnail.absUrl("srcset").split(",");
					var src = srcset[srcset.length - 1].split(" ")[0];
					self.scrapeAsync(src);
					addThumbnailUrl(result, getThumbnail(src));
				} else if (thumbnail.hasAttr("src")){
					var src = thumbnail.absUrl("src");
					self.scrapeAsync(src);
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
					self.scrapeAsync(src);
					addVideoUrl(result, getVideo(src));
				} else if (video.hasAttr("src")) {
					var src = video.absUrl("src");
					self.scrapeAsync(src);
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
					self.scrapeAsync(src);
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

	@CacheEvict(value = {"scrape-config", "scrape-default-config"}, allEntries = true)
	public void clearCache() {
		logger.info("Cleared scrape config cache.");
	}

	@Cacheable("scrape-config")
	@Transactional(readOnly = true)
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public Scrape getConfig(String origin, String url) {
		var configs = refRepository.findAll(
				RefFilter.builder()
					.origin(origin)
					.query("+plugin/scrape").build().spec()).stream()
			.map(r -> r.getPlugins().get("+plugin/scrape"))
			.map(p -> objectMapper.convertValue(p, Scrape.class)).toList();
		for (var c : configs) {
			if (c.getSchemes() == null) continue;
			for (var s : c.getSchemes()) {
				var regex = Pattern.quote(s).replace("*", "\\E.*\\Q");
				if (url.matches(regex)) return c;
			}
		}
		return null;
	}

	@Cacheable("scrape-default-config")
	@Transactional(readOnly = true)
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public Scrape getDefaultConfig(String origin) {
		return refRepository.findOneByUrlAndOrigin("config:scrape-catchall", origin)
			.map(r -> r.getPlugins().get("+plugin/scrape"))
			.map(p -> objectMapper.convertValue(p, Scrape.class))
			.orElse(null);
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
		if (ref.getTags() != null && ref.getTags().contains("plugin/video") && url.endsWith(".m3u8") && !ref.getPlugins().get("plugin/video").get("url").asText().endsWith(".m3u8")) return;
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

	@Timed(value = "jasper.webscrape")
	public String rss(String url) {
		var web = fetch(url);
		if (web.getData() == null) return null;
		var strData = new String(web.getData());
		if (!strData.trim().startsWith("<")) return null;
		var doc = Jsoup.parse(strData);
		return doc.getElementsByTag("link").stream()
			.filter(t -> t.attr("type").equals("application/rss+xml"))
			.filter(t -> t.hasAttr("href"))
			.map(t -> t.attr("href"))
			.findFirst().orElse(null);
	}

	public void scrape(String url) {
		if (isBlank(url)) return;
		if (exists(url)) return;
		fetch(url);
	}

	public void scrapeAsync(String url) {
		if (isBlank(url)) return;
		if (exists(url)) return;
		var web = new Web();
		web.setUrl(url);
		webRepository.save(web);
		scrapeLater.add(url);
	}

	@Scheduled(fixedDelay = 300)
	public void drainAsyncScrape() {
		scrapeLater.drainTo(scraping);
		for (var url : scraping) fetch(url);
		scraping.clear();
	}

	@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
	public void clearFailed() {
		// TODO: Not cluster safe
		// TODO: Make this run on a fixed interval regardless of how many instances are running
		webRepository.deleteAllByDataIsNullAndMimeIsNull();
	}

	@Timed(value = "jasper.webscrape")
	public Web fetch(String url) {
		url = fixUrl(url);
		var maybeWeb = webRepository.findById(url);
		if (maybeWeb.isPresent()) return maybeWeb.get();
		List<String> scrapeMore = List.of();
		try {
			var web = doScrape(url);
			scrapeMore = createArchive(web);
			return webRepository.save(web);
		} catch (Exception e) {
			logger.warn("Error fetching", e);
			return null;
		} finally {
			for (var m : scrapeMore) self.scrapeAsync(m);
		}
	}

	private Web doScrape(String url) throws IOException {
		int timeout = 30 * 1000; // 30 seconds
		RequestConfig requestConfig = RequestConfig
			.custom()
			.setConnectTimeout(timeout)
			.setConnectionRequestTimeout(timeout)
			.setSocketTimeout(timeout).build();
		var builder = HttpClients.custom().setDefaultRequestConfig(requestConfig);
		try (CloseableHttpClient client = builder.build()) {
			HttpUriRequest request = new HttpGet(url);
			request.setHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36");
			try (var res = client.execute(request)) {
				if (res.getStatusLine().getStatusCode() == 301 || res.getStatusLine().getStatusCode() == 304) {
					return doScrape(res.getFirstHeader("Location").getElements()[0].getValue());
				}
				var result = new Web();
				result.setUrl(url);
				result.setMime(res.getFirstHeader("Content-Type").getValue());
				result.setData(res.getEntity().getContent().readAllBytes());
				return result;
			}
		}
	}

	private List<String> createArchive(Web source) {
		var moreScrape = new ArrayList<String>();
		// M3U8 Manifest
		var data = new String(source.getData());
		if (data.trim().startsWith("#") && (source.getUrl().endsWith(".m3u8") || source.getMime().equals("application/x-mpegURL") || source.getMime().equals("application/vnd.apple.mpegurl"))) {
			try {
				var urlObj = new URL(source.getUrl());
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
				source.setData(buffer.toString().getBytes());
			} catch (Exception e) {}
		}
		return moreScrape;
	}

	@Timed(value = "jasper.webscrape")
	public boolean exists(String url) {
		return webRepository.existsById(fixUrl(url));
	}

	@Async
	@Timed(value = "jasper.webscrape")
	public Web cache(Web web) {
		return webRepository.save(web);
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
		self.scrapeAsync(url);
		ref.addTag(tag);
		var img = objectMapper.createObjectNode();
		img.set("url", valueOf(url));
		ref.setPlugin(tag, img);
	}

}
