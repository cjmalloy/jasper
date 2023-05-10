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

	@Timed(value = "jasper.webscrape")
	public Ref scrape(String url) throws IOException, URISyntaxException {
		var result = new Ref();
		var web = fetch(url);
		var strData = new String(web.getData());
		if (!strData.trim().startsWith("<")) return result;
		var doc = Jsoup.parse(strData);
		result.setUrl(url);
		result.setTitle(doc.title());
		return result;
	}

	@Timed(value = "jasper.webscrape")
	public Web fetch(String url) throws URISyntaxException {
		var maybeWeb = webRepository.findById(url);
		if (maybeWeb.isPresent()) return maybeWeb.get();
		var result = new Web();
		result.setUrl(url);
		result.setData(webScraperClient.scrape(new URI(url)));
		return webRepository.save(result);
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
