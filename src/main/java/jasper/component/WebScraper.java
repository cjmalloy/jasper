package jasper.component;

import feign.RetryableException;
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
		}
		return result;
	}

	@Timed(value = "jasper.webscrape")
	public String rss(String url) throws IOException, URISyntaxException {
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
	public Web fetch(String url) throws URISyntaxException, IOException {
		var maybeWeb = webRepository.findById(url);
		if (maybeWeb.isPresent() && maybeWeb.get().getData() != null) return maybeWeb.get();
		var result = new Web();
		result.setUrl(url);
		try {
			var res = webScraperClient.scrape(new URI(url));
			if (res.status() == 301 || res.status() == 304) {
				res = webScraperClient.scrape(new URI(res.headers().get("Location").toString()));
			}
			result.setData(res.body().asInputStream().readAllBytes());
		} catch (RetryableException e) {
			logger.warn("Error fetching", e);
		}
		return webRepository.saveAndFlush(result);
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
