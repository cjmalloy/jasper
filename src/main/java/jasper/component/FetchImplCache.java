package jasper.component;

import jasper.errors.ScrapeProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Profile("!proxy")
@Component
public class FetchImplCache implements Fetch {
	private static final Logger logger = LoggerFactory.getLogger(FetchImplCache.class);

	@Autowired
	ConfigCache configs;

	@Autowired
	Replicator replicator;

	public FileRequest doScrape(String url, String origin) throws IOException {
		var remote = configs.getRemote(origin);
		if (remote == null) throw new ScrapeProtocolException("cache");
		return replicator.fetch(url, remote);
	}

}
