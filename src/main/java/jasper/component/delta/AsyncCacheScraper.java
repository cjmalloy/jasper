package jasper.component.delta;

import jakarta.annotation.PostConstruct;
import jasper.component.FileCache;
import jasper.component.Tagger;
import jasper.domain.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("proxy & file-cache")
@Component
public class AsyncCacheScraper implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(AsyncCacheScraper.class);

	@Autowired
	Async async;

	@Autowired
	FileCache fileCache;

	@Autowired
	Tagger tagger;

	@PostConstruct
	void init() {
		async.addAsyncTag("_plugin/delta/cache", this);
	}

	@Override
	public void run(Ref ref) throws Exception {
		logger.info("{} Caching {}", ref.getOrigin(), ref.getUrl());
		tagger.tag(ref.getUrl(), ref.getOrigin(), "-_plugin/delta/cache", "_plugin/cache");
		try {
			fileCache.refresh(ref.getUrl(), ref.getOrigin());
		} catch (Exception e) {
			tagger.attachError(ref.getOrigin(),
				tagger.internalPlugin(ref.getUrl(), ref.getOrigin(), "_plugin/cache", null, "-_plugin/delta/cache"),
				"Error Fetching for async scrape", e.getMessage());
		}
	}
}
