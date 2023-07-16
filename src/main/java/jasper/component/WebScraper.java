package jasper.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jasper.component.dto.JsonLd;
import jasper.domain.Ref;
import jasper.domain.Web;
import jasper.repository.WebRepository;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.databind.node.TextNode.valueOf;
import static jasper.domain.proj.Tag.hasMedia;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class WebScraper {
	private static final Logger logger = LoggerFactory.getLogger(WebScraper.class);

	@Autowired
	WebRepository webRepository;

	@Autowired
	Sanitizer sanitizer;

	@Autowired
	ObjectMapper objectMapper;

	WebScraper self = this;

	// TODO: Put config in plugin/scrape
	private final String[] websiteTextSelectors = {
		"span[itemprop=articleBody]",
		"section.content__body",
		".article-body__content",
		".article__content",
		".article__content-body",
		".article-body",
		".details>.details-body",
		".f_blog_body",
		".caas-body",
		"article .crawler",
		".fl-module-fl-post-content",
		".body.markup",
		"main.ff-main-content section",
		".single-feature-content",
		".single-content-wrapper",
		".body__inner-container",
		".showblog-body__content",
		".article__body-text",
		".article__content-container",
		".c-article-content",
		"#drr-container",
		".meteredContent",
		".gnt_ar_b",
		".views-article-body",
		".elementor-widget-theme-post-content",
		".l-article__text",
		".article__text",
		".sdc-article-body--story",
		".wprm-recipe-container",
		".tasty-recipes",
		".storytext",
		".rich-text",
		".entry-content",
		".entry-summary",
		".entrytext",
		".articleBody",
		".mv-create-wrapper",
		".content-body",
		".post_page-content",
		".post-body",
		".td-post-content",
		".post_content",
		".tam__single-content-output",
		".js_post-content",
		".js_starterpost",
		".sqs-layout",
		".c-text",
		".pmc-paywall",
		".postcontent",
		".post-content",
		"article.article-body-wrapper-custom",
		"main#main .container-md dl",
		"main .item-details-inner",
		"#comic",
		"#liveblog-body",
		"#content-blocks",
		"#article-body-content",
		"#article",
		"#article-body",
		"#main",
		"#body",
		"#mainbar",
		"#maincontent",
		"#bodyContent",
		"#content",
		"#item-content-data",
		".wysiwyg--all-content",
		".hide-mentions",
		"div.article",
		"div.content",
		"div[class^=article-body__content]",
		"div[class^=article-body__container]",
		"section[class^=ArticleBody_root]",
		"div[class^=NodeContent_body]",
		"div[class^=c-article-body]",
		"div[class^=ArticlePageChunksContent]",
		"div[class^=DraftjsBlocks_draftjs]",
		"div[class^=index-module_storyContainer]",
		".story",
		".post-content",
		".blog-content",
		".article-body",
		".article-content",
		"main.main",
		".page-content",
		".post",
		".grid-body",
		".body",
		".body-text",
		"article",
		"main",
		"section"
	};

	private final String[] removeSelectors = {
		"nav",
		"header",
		"footer",
		"aside",
		"noscript",
		".comment-link",
		".date-info .updated",
		".ad-container",
		".ad-unit",
		".advert-wrapper",
		".af-slim-promo",
		".wsj-ad",
		".c-ad",
		".adLabelWrapperManual",
		".ad",
		"gu-island",
		"div[class^=z-ad]",
		"div[class^=ad_]",
		".liveBlogCards",
		".speaker-mute",
		".brands-most-popular",
		".z-trending-headline",
		".article__gallery-count",
		".rail-component",
		".read-more-container",
		"#site_atribution",
		"#site_attribution",
		".article-extras",
		"#firefly-poll-container",
		".subscriber-only.encrypted-content",
		".support-us2",
		".thm-piano-promo",
		".article-content-cta-group",
		".nlp-ignore-block",
		".block-ibtg-article",
		".quickview__meta",
		".stat__item--recipe-box",
		".stat__item--print",
		".internallink",
		".credit-caption",
		".js-is-sticky-social--bottom",
		".js-kaf-share-widget",
		".wprm-call-to-action",
		".cwp-food-buttons",
		".tasty-recipes-buttons",
		".recipe-bakers-hotline",
		"#block-kafrecommendedrecipes",
		"#news_letter_div",
		"#firefly-poll-container",
		"#in-article-trending",
		"#in-article-related",
		"#inline-article-recommend",
		".inline-article",
		".inline-collection",
		".hidden-print",
		"[data-block=doNotPrint]",
		".apester-media",
		".ff-fancy-header-container",
		".entry-submit-correction",
		".correction-form",
		".ff-truth-accuracy-text",
		".author-info",
		".type-commenting",
		".sponsor",
		".region-content-footer",
		".hide-for-print",
		".button-wrapper",
		".subscription-widget-wrap",
		".sam-pro-place",
		".subscribe-widget",
		"#trinity-audio-table",
		".trinity-skip-it",
		".big-top",
		"#tncms-region-article_instory_top",
		".novashare-ctt",
		".w-primis-article",
		".article-btm-content",
		".gb-container",
		".pp-multiple-authors-wrapper",
		".social-tools",
		".subscriber-offers",
		".meks_ess",
		".pop-up-bar",
		".fancy-box",
		".c-figure__expand",
		".van-image-figure",
		".abh_box",
		".linkstack",
		".code-block",
		".article-sharing",
		".heateor_sssp_sharing_container",
		".related",
		".noprint",
		".printfooter",
		".mw-editsection",
		".catlinks",
		".video-top-container",
		".newsletter-component",
		".thehill-promo-link",
		".page-actions",
		".post-tags",
		".share-container",
		".lsn-petitions",
		".donate-callout",
		".enhancement",
		".div-related-embed",
		".article-related-inline",
		".author-endnote-container",
		".newsletter-form-wrapper",
		".related_topics",
		".jp-relatedposts",
		".post-tools-wrapper",
		".js_alerts_modal",
		".js_comments-iframe",
		".share-label-text",
		".share-toolbar-container",
		".social-share-links",
		".article-trust-bar",
		".l-inlineStories",
		".c-conversation-title",
		".c-pullquote",
		".c-signin",
		".c-disclaimer",
		".overlay",
		".control",
		".inline-video",
		"div[data-component=video-block]",
		".instream-native-video",
		".embed-frame",
		".video-schema",
		".meta.player",
		".ml-subscribe-form",
		".dfm-trust-indicators-footer-links",
		"section .related_posts",
		".wp-block-algori-social-share-buttons-block-algori-social-share-buttons",
		".related_title",
		".tags .my_tag",
		".relatedbox",
		".blog-subscribe",
		".ns-share-count",
		".sharedaddy",
		".scope-web\\|mobileapps",
		"[class*=SnippetSubheadline]",
		"[class*=SnippetSignInText]",
		"a[href^=https://subscribe.wsj.com/wsjsnippet]",
		"a[href^=https://www.foxnews.com/apps-products]",
		"a[href^=https://www.ctvnews.ca/newsletters]",
		"a[href^=https://www.ctvnews.ca/app]",
		"ul[class^=context-widget__tabs]",
		"div[class^=index-module_shareColumn]",
		"div[class^=index-module_overlayWrapper]",
		"div[class^=UserWayButton]",
		"div[class^=article-body__desktop-only]",
		"div[class^=article-body__top-toolbar-container]",
		"div[class^=frontend-components-DigestPostEmbed]",
		"div[class^=article-toolbar]",
		"div[class^=RelatedStories_relatedStories]",
		"div[class^=ArticleWeb_shareBottom]",
		"div[class^=RelatedTopics_relatedTopics]",
		"div[class^=ArticleWeb_publishedDate]",
		"p[class^=ArticleRelatedContentLink_root]",
		"img[style^=width:1px;height:1px]",
		"img[style^=position:absolute;width:1px;height:1px]",
		"div:empty",
		"span:empty",
		"li:empty",
		"ul:empty",
		"ol:empty",

		// TODO: add image gallery plugin
		".article-slideshow",
	};

	private final String[] removeAfterSelectors = {
		".elementor-location-single"
	};

	private final String[] removeStyleSelectors = {
		".subscriber-only"
	};

	private final String[] imageFixSelectors = {
		"\\?w=\\d+&h=\\d+(&crop=\\d+)?",
	};

	private final String[] imageSelectors = {
		"#item-image #bigimage img",
		".detail-item-img img",
		"#pvImageInner noscript a",
	};

	private final String[] videoSelectors = {
		"video[src]",
		"video source",
		"div.video[data-stream]",
	};

	private final String[] audioSelectors = {
		"audio[src]",
		"audio source",
	};

	private final String[] thumbnailSelectors = {
		"figure.entry-thumbnail img",
		".live-blog-above-main-content svg",
		"figure.embed link[as=image][rel=preload]",
		"img.video__fallback",
		"amp-img",
	};

	@Timed(value = "jasper.webscrape")
	public Ref web(String url) throws IOException, URISyntaxException {
		var result = new Ref();
		result.setUrl(url);
		var web = fetch(url);
		if (web != null && web.getData() != null) {
			var strData = new String(web.getData());
			if (!strData.trim().startsWith("<")) return result;
			var doc = Jsoup.parse(strData, url);
			result.setTitle(doc.title());
			// TODO: Parse plugins separately
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
				for (var query : imageFixSelectors) {
					if (src.matches(query)) {
						image.attr("src", src.replaceAll(query, ""));
						break;
					}
				}
			}
			for (var s : imageSelectors) {
				var image = doc.select(s).first();
				if (image == null) continue;
				if (image.tagName().equals("a")) {
					var src = image.absUrl("href");
					self.scrape(src);
					addPluginUrl(result, "plugin/image", getImage(src));
					addThumbnailUrl(result, getThumbnail(src));
				} else {
					var src = image.absUrl("src");
					self.scrape(src);
					addPluginUrl(result, "plugin/image", getImage(src));
					addThumbnailUrl(result, getThumbnail(src));
					image.parent().remove();
				}
			}
			for (var s : thumbnailSelectors) {
				for (var thumbnail : doc.select(s)) {
					if (thumbnail.tagName().equals("svg")) {
						addThumbnailUrl(result, svgToUrl(sanitizer.clean(thumbnail.outerHtml(), url)));
					} else if (thumbnail.hasAttr("href")) {
						var src = thumbnail.absUrl("href");
						self.scrape(src);
						addThumbnailUrl(result, getThumbnail(src));
					} else if (thumbnail.hasAttr("src")) {
						var src = thumbnail.absUrl("src");
						self.scrape(src);
						addThumbnailUrl(result, getThumbnail(src));
					}
					thumbnail.parent().remove();
				}
			}
			for (var r : removeSelectors) doc.select(r).remove();
			for (var r : removeStyleSelectors) doc.select(r).removeAttr("style");
			for (var s : videoSelectors) {
				for (var video : doc.select(s)) {
					if (video.tagName().equals("div")) {
						var src = video.absUrl("data-stream");
						self.scrape(src);
						addVideoUrl(result, getVideo(src));
					} else if (video.hasAttr("src")) {
						var src = video.absUrl("src");
						self.scrape(src);
						addVideoUrl(result, getVideo(src));
						addWeakThumbnail(result, getThumbnail(src));
						video.parent().remove();
					}
				}
			}
			for (var v : doc.select("video")) {
				if (v.select("source").isEmpty()) v.remove();
			}
			for (var s : audioSelectors) {
				for (var audio : doc.select(s)) {
					if (audio.hasAttr("src")) {
						var src = audio.absUrl("src");
						self.scrape(src);
						addPluginUrl(result, "plugin/audio", getVideo(src));
						audio.parent().remove();
					}
				}
			}
			for (var a : doc.select("audio")) {
				if (a.select("source").isEmpty()) a.remove();
			}
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
			for (var s : websiteTextSelectors) {
				var el = doc.body().select(s).first();
				if (el != null) {
					for (var r : removeAfterSelectors) el.select(r).remove();
					result.setComment(sanitizer.clean(el.html(), url));
					return result;
				}
			}
			result.setComment(doc.body().wholeText().trim());
		}
		return result;
	}

	private void addWeakThumbnail(Ref ref, String url) {
		if (!ref.getTags().contains("plugin/thumbnail")) addThumbnailUrl(ref, url);
	}

	private void addThumbnailUrl(Ref ref, String url) {
		if (url.endsWith(".com") || url.endsWith(".m3u8")) return;
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
		// TODO: Switch to async tag processor using "_scrape" tag
		if (exists(url)) return;
		this.fetch(url);
	}

	@Timed(value = "jasper.webscrape")
	public Web fetch(String url) {
		url = fixUrl(url);
		var maybeWeb = webRepository.findById(url);
		if (maybeWeb.isPresent() && maybeWeb.get().getData() != null) return maybeWeb.get();
		List<String> scrapeMore = List.of();
		try {
			var web = doScrape(url);
			scrapeMore = createArchive(web);
			return webRepository.saveAndFlush(web);
		} catch (Exception e) {
			logger.warn("Error fetching", e);
			return null;
		} finally {
			for (var m : scrapeMore) self.scrape(m);
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
		self.scrape(url);
		ref.addTag(tag);
		var img = objectMapper.createObjectNode();
		img.set("url", valueOf(url));
		ref.setPlugin(tag, img);
	}

}
