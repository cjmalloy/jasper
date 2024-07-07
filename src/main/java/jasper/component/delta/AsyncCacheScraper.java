package jasper.component.delta;

import jasper.component.ConfigCache;
import jasper.component.FileCache;
import jasper.domain.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Profile("proxy & file-cache")
@Component
public class AsyncCacheScraper implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(AsyncCacheScraper.class);

	@Autowired
	Async async;

	@Autowired
	FileCache fileCache;

	@Autowired
	ConfigCache configs;

	@PostConstruct
	void init() {
		async.addAsyncTag("_plugin/delta/cache", this);
	}

	@Override
	public void run(Ref ref) throws Exception {
		fileCache.refresh(ref.getUrl(), ref.getOrigin());
	}
}
