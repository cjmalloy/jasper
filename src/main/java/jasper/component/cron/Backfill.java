package jasper.component.cron;

import jasper.component.ConfigCache;
import jasper.component.Meta;
import jasper.config.Props;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class Backfill {
	private static final Logger logger = LoggerFactory.getLogger(Backfill.class);

	@Autowired
	Props props;

	@Autowired
	ConfigCache configs;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Meta meta;

	@Scheduled(fixedRate = 60, initialDelay = 10, timeUnit = TimeUnit.SECONDS)
	public void backfill() {
		if (!configs.root().script("+plugin/backfill")) return;
		for (var origin : configs.root().scriptOrigins("+plugin/backfill")) {
			backfillOrigin(origin);
		}
	}

	private void backfillOrigin(String origin) {
		for (var i = 0; i < props.getBackfillBatchSize(); i++) {
			var ref = refRepository.getRefBackfill(origin).orElse(null);
			if (ref == null) return;
			logger.trace("{} Backfilling ref ({}) {}: {}",
				origin, ref.getOrigin(), ref.getTitle(), ref.getUrl());
			meta.regen(origin, ref);
			try {
				refRepository.save(ref);
			} catch (Exception e) {
				logger.error("{} Error backfilling: {}", origin, ref.getUrl(), e);
				return;
			}
		}
	}
}
