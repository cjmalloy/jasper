package jasper.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import jasper.client.JasperClient;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.plugin.Origin;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
public class Replicator {
	private static final Logger logger = LoggerFactory.getLogger(Replicator.class);

	@Autowired
	Props props;

	@Autowired
	RefRepository refRepository;
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

	@Autowired
	Meta meta;

	@Autowired
	ObjectMapper objectMapper;

	@Counted("jasper.replicate")
	public void replicate(Ref origin) {
		var config = objectMapper.convertValue(origin.getPlugins().get("+plugin/origin"), Origin.class);
		config.setLastScrape(Instant.now());
		origin.getPlugins().set("+plugin/origin", objectMapper.convertValue(config, JsonNode.class));
		refRepository.save(origin);

		Map<String, Object> options = new HashMap<>();
		options.put("size", props.getReplicateBatch());
		options.put("origin", config.getRemote());
		try {
			var url = new URI(isNotBlank(config.getProxy()) ? config.getProxy() : origin.getUrl());
			options.put("modifiedAfter", refRepository.getCursor(config.getOrigin()));
			for (var ref : client.ref(url, options)) {
				config.migrate(ref);
				var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
				meta.update(ref, maybeExisting.orElse(null));
				refRepository.save(ref);
			}
			options.put("modifiedAfter", extRepository.getCursor(config.getOrigin()));
			for (var ext : client.ext(url, options)) {
				if (config.skip(ext.getTag())) continue;
				config.migrate(ext);
				extRepository.save(ext);
			}
			options.put("modifiedAfter", userRepository.getCursor(config.getOrigin()));
			for (var user : client.user(url, options)) {
				if (config.skip(user.getTag())) continue;
				config.migrate(user);
				userRepository.save(user);
			}
			options.put("modifiedAfter", pluginRepository.getCursor(config.getOrigin()));
			for (var plugin : client.plugin(url, options)) {
				if (config.skip(plugin.getTag())) continue;
				config.migrate(plugin);
				pluginRepository.save(plugin);
			}
			options.put("modifiedAfter", templateRepository.getCursor(config.getOrigin()));
			for (var template : client.template(url, options)) {
				if (config.skip(template.getTag())) continue;
				config.migrate(template);
				templateRepository.save(template);
			}
		} catch (Exception e) {
			logger.error("Error replicating origin {}", config.getOrigin(), e);
		}
	}

}
