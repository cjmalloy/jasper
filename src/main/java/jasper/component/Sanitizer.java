package jasper.component;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class Sanitizer {

	private final Safelist whitelist = Safelist.relaxed()
		.addAttributes(":all", "style")
		.addAttributes(":all", "width")
		.addAttributes(":all", "width");

	public String clean(String html) {
		return clean(html, null);
	}

	public String clean(String html, String url) {
		return url == null
			? Jsoup.clean(html, whitelist)
			: Jsoup.clean(html, url, whitelist);
	}
}
