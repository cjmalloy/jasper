package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
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

import static com.fasterxml.jackson.databind.node.TextNode.valueOf;
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

	// TODO: Put config in plugin/scrape
	private final String[] websiteTextSelectors = {
		".article-body__content",
		".article-body",
		".f_blog_body",
		".fl-module-fl-post-content",
		".body.markup",
		"main.ff-main-content section",
		".single-feature-content",
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
		".rich-text",
		".entrytext",
		".entry-content",
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
		"main#main .container-md dl",
		"main .item-details-inner",
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
		"div[class^=article-body__content]",
		"div[class^=article-body__container]",
		"section[class^=ArticleBody_root]",
		"div[class^=NodeContent_body]",
		"div[class^=c-article-body]",
		"div[class^=ArticlePageChunksContent]",
		"div[class^=DraftjsBlocks_draftjs]",
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
		".af-slim-promo",
		".wsj-ad",
		".c-ad",
		".ad",
		"div[class^=z-ad]",
		".liveBlogCards",
		".speaker-mute",
		".z-trending-headline",
		".support-us2",
		".thm-piano-promo",
		".quickview__meta",
		".stat__item--recipe-box",
		".stat__item--print",
		".js-is-sticky-social--bottom",
		".js-kaf-share-widget",
		".wprm-call-to-action",
		".cwp-food-buttons",
		".tasty-recipes-buttons",
		".recipe-bakers-hotline",
		"#block-kafrecommendedrecipes",
		"#firefly-poll-container",
		"#in-article-trending",
		"#in-article-related",
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
		".novashare-ctt",
		".w-primis-article",
		".article-btm-content",
		".gb-container",
		".pp-multiple-authors-wrapper",
		".social-tools",
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
		".ml-subscribe-form",
		"section .related_posts",
		".wp-block-algori-social-share-buttons-block-algori-social-share-buttons",
		".related_title",
		".tags .my_tag",
		".relatedbox",
		".blog-subscribe",
		".ns-share-count",
		".sharedaddy",
		".scope-web\\|mobileapps",
		"a[href^=https://www.foxnews.com/apps-products]",
		"div[class^=article-body__top-toolbar-container]",
		"div[class^=frontend-components-DigestPostEmbed]",
		"div[class^=article-toolbar]",
		"div[class^=RelatedStories_relatedStories]",
		"div[class^=ArticleWeb_shareBottom]",
		"div[class^=RelatedTopics_relatedTopics]",
		"div[class^=ArticleWeb_publishedDate]",
		"p[class^=ArticleRelatedContentLink_root]",
		"img[style^=width:1px;height:1px]",
		"img[style^=position:absolute;width:1px;height:1px]"
	};

	private final String[] removeAfterSelectors = {
		".elementor-location-single"
	};

	private final String[] imageFixSelectors = {
		"\\?w=\\d+&h=\\d+(&crop=\\d+)?",
	};

	private final String[] imageSelectors = {
		"#item-image #bigimage img",
		".detail-item-img img",
	};

	private final String[] imageHrefSelectors = {
		"#pvImageInner noscript a",
	};

	private final String[] thumbnailSelectors = {
		"figure.entry-thumbnail img",
		".live-blog-above-main-content svg",
		"figure.embed link[as=image][rel=preload]",
		"amp-img",
	};

	@Timed(value = "jasper.webscrape")
	public Ref web(String url) throws IOException, URISyntaxException {
		var result = new Ref();
		result.setUrl(url);
		var web = fetch(url);
		if (web.getData() != null) {
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
			for (var s : imageHrefSelectors) {
				var a = doc.select(s).first();
				if (a != null) {
					var src = a.absUrl("href");
					addPluginUrl(result, "plugin/image", getImage(src));
					addPluginUrl(result, "plugin/thumbnail", getThumbnail(src));
				}
			}
			for (var s : imageSelectors) {
				var image = doc.select(s).first();
				if (image != null) {
					var src = image.absUrl("src");
					addPluginUrl(result, "plugin/image", getImage(src));
					addPluginUrl(result, "plugin/thumbnail", getThumbnail(src));
					image.parent().remove();
				}
			}
			for (var s : thumbnailSelectors) {
				var thumbnail = doc.select(s).first();
				if (thumbnail != null) {
					if (thumbnail.tagName().equals("svg")) {
						addPluginUrl(result, "plugin/thumbnail", svgToUrl(sanitizer.clean(thumbnail.outerHtml(), url)));
					} else if (thumbnail.hasAttr("href")) {
						var src = thumbnail.absUrl("href");
						addPluginUrl(result, "plugin/thumbnail", getThumbnail(src));
					} else if (thumbnail.hasAttr("src")) {
						var src = thumbnail.absUrl("src");
						addPluginUrl(result, "plugin/thumbnail", getThumbnail(src));
					}
					thumbnail.parent().remove();
				}
			}
			for (var r : removeSelectors) doc.select(r).remove();
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

	private String getImage(String src) {
		if (src.contains("/full/max/0/")) return src.replace("/full/max/0/", "/full/!1920,1080/0/");
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

	@Timed(value = "jasper.webscrape")
	public Web fetch(String url) {
		url = fixUrl(url);
		var maybeWeb = webRepository.findById(url);
		if (maybeWeb.isPresent() && maybeWeb.get().getData() != null) return maybeWeb.get();
		try {
			return webRepository.saveAndFlush(doScrape(url));
		} catch (Exception e) {
			logger.warn("Error fetching", e);
			return null;
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
		return url.replaceAll("%20", "+");
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
		ref.addTag(tag);
		var img = objectMapper.createObjectNode();
		img.set("url", valueOf(url));
		ref.setPlugin(tag, img);
	}

}
