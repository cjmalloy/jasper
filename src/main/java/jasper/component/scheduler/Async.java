package jasper.component.scheduler;

import jasper.config.Props;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Profile("async")
@Component
public class Async {
	private static final Logger logger = LoggerFactory.getLogger(Async.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Props props;

	Instant lastModified = Instant.now().minus(1, ChronoUnit.DAYS);

	Map<String, AsyncRunner> tasks = new HashMap<>();

	@Scheduled(fixedDelay = 500)
	public void drainAsyncTask() {
		while (true) {
			var maybeRef = refRepository.findAll(RefFilter.builder()
				.query(trackingQuery())
				.modifiedAfter(lastModified).build().spec(), PageRequest.of(0, 1, Sort.by("modified")));
			if (maybeRef.isEmpty()) return;
			var ref = maybeRef.getContent().get(0);
			lastModified = ref.getModified();
			tasks.forEach((k, v) -> {
				if (!ref.getTags().contains(k)) return;
				try {
					v.run(ref);
				} catch (Exception e) {
					logger.error("Error in async task {} ", k, e);
				}
			});
		}
	}

	public void addAsync(String plugin, AsyncRunner r) {
		tasks.put(plugin, r);
	}

	String trackingQuery() {
		return String.join("|", tasks.keySet());
	}

	public interface AsyncRunner {
		void run(Ref ref);
	}
}
