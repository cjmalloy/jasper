package jasper.component;

import jasper.client.JasperClient;
import jasper.domain.Origin;
import jasper.repository.ExtRepository;
import jasper.repository.FeedRepository;
import jasper.repository.OriginRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class Replicator {
	private static final Logger logger = LoggerFactory.getLogger(Replicator.class);

	@Autowired
	OriginRepository originRepository;
	@Autowired
	RefRepository refRepository;
	@Autowired
	FeedRepository feedRepository;
	@Autowired
	ExtRepository extRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	PluginRepository pluginRepository;
	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	JasperClient client;

	@Scheduled(
		fixedRateString = "${application.replicate-interval-min}",
		initialDelayString = "${application.replicate-delay-min}",
		timeUnit = TimeUnit.MINUTES)
	public void burst() {
		logger.info("Replicating all origins in a burst.");
		var origins = originRepository.findAll();
		for (Origin origin :  origins) {
			origin.setLastScrape(Instant.now());
			replicate(origin);
		}
	}

	private void replicate(Origin origin) {
		Map<String, Object> options = new HashMap<>();
		options.put("size", 5000);
		try {
			var url = new URI(isNotBlank(origin.getProxy()) ? origin.getProxy() : origin.getUrl());
			options.put("modifiedAfter", refRepository.getCursor(origin.getOrigin()));
			for (var ref : client.ref(url, options)) {
				ref.setOrigin(origin.getOrigin());
				refRepository.save(ref);
			}
			options.put("modifiedAfter", feedRepository.getCursor(origin.getOrigin()));
			for (var feed : client.feed(url, options)) {
				feed.setOrigin(origin.getOrigin());
				feedRepository.save(feed);
			}
			options.put("modifiedAfter", extRepository.getCursor(origin.getOrigin()));
			for (var ext : client.ext(url, options)) {
				ext.setOrigin(origin.getOrigin());
				extRepository.save(ext);
			}
			options.put("modifiedAfter", userRepository.getCursor(origin.getOrigin()));
			for (var user : client.user(url, options)) {
				user.setOrigin(origin.getOrigin());
				userRepository.save(user);
			}
			options.put("modifiedAfter", pluginRepository.getCursor(origin.getOrigin()));
			for (var plugin : client.plugin(url, options)) {
				plugin.setOrigin(origin.getOrigin());
				pluginRepository.save(plugin);
			}
			options.put("modifiedAfter", templateRepository.getCursor(origin.getOrigin()));
			for (var template : client.template(url, options)) {
				template.setOrigin(origin.getOrigin());
				templateRepository.save(template);
			}
		} catch (Exception e) {
			logger.error("Error replicating origin {}", origin.getOrigin(), e);
		}
		originRepository.save(origin);
	}

}
