package jasper.component;

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

@Component
public class WebScraper {
	private static final Logger logger = LoggerFactory.getLogger(WebScraper.class);

	@Autowired
	WebRepository webRepository;

	@Autowired
	WebScraperClient webScraperClient;

	private final String[] websiteTextSelectors = {
		".body__inner-container",
		".showblog-body__content",
		".article__body-text",
		".article__content-container",
		".c-article-content",
		".meteredContent",
		".gnt_ar_b",
		".l-article__text",
		".article__text",
		".sdc-article-body--story",
		".rich-text",
		"#main",
		"#mainbar",
		"#maincontent",
		"#bodyContent",
		"#content",
		".wysiwyg--all-content",
		"div[class^=article-body__content]",
		"div[class^=article-body__container]",
		"div[class^=c-article-body]",
		"div[class^=ArticlePageChunksContent]",
		"div[class^=DraftjsBlocks_draftjs]",
		".story",
		".blog-content",
		".article-content",
		".article-body",
		".page-content",
		".grid-body",
		".body",
		"article",
		"main",
		"section"
	};

	private final String[] removeSelectors = {
		"noscript",
		".ad-container",
		".ad-unit",
		".ad",
		".hide-for-print",
		".social-tools",
		".pop-up-bar",
		".linkstack",
		".article-sharing",
		".related",
		".noprint",
		".printfooter",
		".mw-editsection",
		".catlinks",
		".video-top-container",
		".newsletter-component",
		".thehill-promo-link",
		".page-actions",
		".enhancement",
		".article-related-inline",
		".author-endnote-container",
		".related_topics",
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
		"a[href^=https://www.foxnews.com/apps-products]",
		"div[class^=article-body__top-toolbar-container]",
		"div[class^=article-toolbar]"
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
			for (var s : removeSelectors) {
				doc.select(s).remove();
			}
			for (var s : websiteTextSelectors) {
				if (!doc.body().select(s).isEmpty()) {
					result.setComment(doc.body().select(s).first().html().trim());
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
		return webRepository.existsById(url);
	}

	@Async
	@Timed(value = "jasper.webscrape")
	public void cache(Web web) {
		webRepository.save(web);
	}

}
