package jasper.component.delta;

import jasper.component.ConfigCache;
import jasper.component.Ingest;
import jasper.component.Scraper;
import jasper.domain.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Profile("proxy")
@Component
public class AsyncWebScraper implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(AsyncWebScraper.class);

	@Autowired
	Async async;

	@Autowired
	Scraper scraper;

	@Autowired
	Ingest ingest;

	@Autowired
	ConfigCache configs;

	@PostConstruct
	void init() {
		async.addAsyncTag("_plugin/delta/scrape", this);
	}

	@Override
	public void run(Ref ref) throws Exception {
		ref.setComment(scraper.web(ref.getUrl(), ref.getOrigin()).getComment());
		// TODO: scrape other fields
		ref.removeTag("_plugin/delta/scrape");
		ingest.update(ref, false);
	}
}
