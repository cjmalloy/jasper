package jasper.component.channel.delta;

import jasper.component.ConfigCache;
import jasper.component.WebScraper;
import jasper.domain.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Profile("storage")
@Component
public class AsyncWebScraper implements Async.AsyncWatcher {
	private static final Logger logger = LoggerFactory.getLogger(AsyncWebScraper.class);

	@Autowired
	Async async;

	@Autowired
	WebScraper webScraper;

	@Autowired
	ConfigCache configs;

	@PostConstruct
	void init() {
		async.addAsyncTag("_plugin/cache/async", this);
	}

	@Override
	public void run(Ref ref) throws Exception {
		webScraper.scrapeAsync(ref.getUrl(), ref.getOrigin());
	}
}
