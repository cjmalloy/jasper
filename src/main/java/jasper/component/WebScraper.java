package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Ref;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class WebScraper {
	private static final Logger logger = LoggerFactory.getLogger(WebScraper.class);

	@Timed(value = "jasper.webscrape")
	public Ref scrape(String url) throws IOException {
		var doc = Jsoup.connect(url).get();
		var result = new Ref();
		result.setUrl(url);
		result.setTitle(doc.title());
		var lastModified = doc.connection().response().header("Last-Modified");
		if (lastModified != null) {
			result.setPublished(ZonedDateTime.parse(lastModified, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
		}
		return result;
	}

}
