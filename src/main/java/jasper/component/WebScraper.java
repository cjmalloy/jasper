package jasper.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jasper.client.WebScraperClient;
import jasper.domain.Ref;
import jasper.domain.Web;
import jasper.repository.WebRepository;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static com.fasterxml.jackson.databind.node.TextNode.valueOf;

@Component
public class WebScraper {
	private static final Logger logger = LoggerFactory.getLogger(WebScraper.class);

	@Autowired
	WebRepository webRepository;

	@Autowired
	WebScraperClient webScraperClient;

	@Autowired
	Sanitizer sanitizer;

	@Autowired
	ObjectMapper objectMapper;

	// TODO: Put config in plugin/scrape
	private final String[] websiteTextSelectors = {
		".article-body",
		".f_blog_body",
		".fl-module-fl-post-content",
		".body.markup",
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
		"footer",
		"aside",
		"noscript",
		".comment-link",
		".date-info .updated",
		".ad-container",
		".ad-unit",
		".af-slim-promo",
		".wsj-ad",
		".ad",
		"div[class^=z-ad]",
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
		"p[class^=ArticleRelatedContentLink_root]"
	};

	private final String[] removeAfterSelectors = {
		".elementor-location-single"
	};

	@Timed(value = "jasper.webscrape")
	public Ref web(String url) throws IOException, URISyntaxException {
		var result = new Ref();
		result.setUrl(url);
		var web = fetch(url);
		if (web.getData() != null) {
			var strData = new String(web.getData());
			if (!strData.trim().startsWith("<")) return result;
			var doc = Jsoup.parse(strData);
			result.setTitle(doc.title());
			var thumbnail = doc.select("figure.entry-thumbnail img").first();
			if (thumbnail != null) {
				// TODO: Parse thumbnail and published separately
				result.addTag("plugin/thumbnail");
				var thumb = objectMapper.createObjectNode();
				thumb.set("url", valueOf(thumbnail.attr("src")));
				result.setPlugin("plugin/thumbnail", thumb);
				thumbnail.parent().remove();
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
			var res = webScraperClient.scrape(new URI(url));
			if (res.status() == 301 || res.status() == 304) {
				res = webScraperClient.scrape(new URI(res.headers().get("Location").toString()));
			}
			var result = new Web();
			result.setUrl(url);
			result.setData(res.body().asInputStream().readAllBytes());
			return webRepository.saveAndFlush(result);
		} catch (Exception e) {
			logger.warn("Error fetching", e);
			return null;
		}
	}

	@Timed(value = "jasper.webscrape")
	public boolean exists(String url) {
		return webRepository.existsById(fixUrl(url));
	}

	@Async
	@Timed(value = "jasper.webscrape")
	public void cache(Web web) {
		webRepository.save(web);
	}

	private String fixUrl(String url) {
		return url.replaceAll("%20", "+");
	}

}
