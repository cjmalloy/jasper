package jasper.component.delta;

import jasper.component.Storage;
import jasper.component.WebScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Profile("recycler")
@Component
public class Recycler {
	private static final Logger logger = LoggerFactory.getLogger(Recycler.class);

	@Autowired
	Storage storage;

	@Autowired
	WebScraper webScraper;

	@Scheduled(fixedDelay = 24, initialDelay = 24, timeUnit = TimeUnit.HOURS)
	public void clearDeleted() {
		storage.visitTenants(webScraper::clearDeleted);
	}
}
