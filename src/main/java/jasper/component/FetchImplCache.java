package jasper.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static jasper.domain.proj.HasTags.hasMatchingTag;

@Profile("!proxy")
@Component
public class FetchImplCache implements Fetch {
	private static final Logger logger = LoggerFactory.getLogger(FetchImplCache.class);

	@Autowired
	ConfigCache configs;

	@Autowired
	Replicator replicator;

	public FileRequest doScrape(String url, String origin) {
		var remote = configs.getRemote(origin);
		if (remote == null || hasMatchingTag(remote, "+plugin/error")) return null;
		return replicator.fetch(url, remote);
	}

}
