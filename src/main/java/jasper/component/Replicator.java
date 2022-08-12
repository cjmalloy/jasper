package jasper.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.client.JasperClient;
import jasper.domain.Ref;
import jasper.domain.plugin.Origin;
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
	ObjectMapper objectMapper;


	public void replicate(Ref origin) {
		var config = objectMapper.convertValue(origin.getPlugins().get("+plugin/origin"), Origin.class);
		Map<String, Object> options = new HashMap<>();
		options.put("size", 5000);
		try {
			var url = new URI(isNotBlank(config.getProxy()) ? config.getProxy() : origin.getUrl());
			options.put("modifiedAfter", refRepository.getCursor(origin.getOrigin()));
			for (var ref : client.ref(url, options)) {
				ref.setOrigin(origin.getOrigin());
				refRepository.save(ref);
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
		config.setLastScrape(Instant.now());
		origin.getPlugins().set("+plugin/origin", objectMapper.convertValue(config, JsonNode.class));
		refRepository.save(origin);
	}

}
