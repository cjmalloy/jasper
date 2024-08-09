package jasper.component.delta;

import jasper.component.ConfigCache;
import jasper.component.Ingest;
import jasper.component.Scraper;
import jasper.component.Tagger;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;

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
	Tagger tagger;

	@Autowired
	ConfigCache configs;

	@Autowired
	RefRepository refRepository;

	@Autowired
	EntityManager em;

	@PostConstruct
	void init() {
		async.addAsyncTag("_plugin/delta/scrape", this);
	}

	@Override
	public void run(Ref ref) throws Exception {
		if (ref.hasTag("_plugin/delta/cache")) {
			logger.debug("{} Waiting for async cache {}", ref.getOrigin(), ref.getUrl());
			return;
		}
		if (!ref.hasTag("_plugin/cache")) {
			logger.debug("{} Deferring scrape until cached {}", ref.getOrigin(), ref.getUrl());
			tagger.tag(ref.getUrl(), ref.getOrigin(), "_plugin/delta/cache");
			return;
		}
		logger.info("{} Scraping {}", ref.getOrigin(), ref.getUrl());
		var web = scraper.web(ref.getUrl(), ref.getOrigin());
		em.detach(ref);
		var scrapeAll = ref.hasTag("_plugin/delta/scrape/ref");
		if (scrapeAll || ref.hasTag("_plugin/delta/scrape/title")) ref.setTitle(web.getTitle());
		if (scrapeAll || ref.hasTag("_plugin/delta/scrape/comment")) ref.setComment(web.getComment());
		if (scrapeAll || ref.hasTag("_plugin/delta/scrape/sources")) ref.setSources(web.getSources());
		if (scrapeAll || ref.hasTag("_plugin/delta/scrape/alts")) ref.setAlternateUrls(web.getAlternateUrls());
		if (scrapeAll || ref.hasTag("_plugin/delta/scrape/plugins")) ref.setPlugins(web.getPlugins());
		if (scrapeAll || ref.hasTag("_plugin/delta/scrape/tags")) ref.setTags(web.getTags());
		if (scrapeAll || ref.hasTag("_plugin/delta/scrape/published")) ref.setPublished(web.getPublished());
		ref.removePrefixTags();
		ref.removeTag("_plugin/delta/scrape");
		ingest.update(ref, false);
	}
}
