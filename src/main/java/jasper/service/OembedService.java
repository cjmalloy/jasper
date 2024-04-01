package jasper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jasper.client.OembedClient;
import jasper.component.OembedProviders;
import jasper.config.Props;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

@Service
public class OembedService {
	private static final Logger logger = LoggerFactory.getLogger(OembedService.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	OembedClient oembedClient;

	@Autowired
	OembedProviders oembedProviders;

	@Autowired
	Auth auth;

	@Autowired
	Props props;

	@Autowired
	ObjectMapper objectMapper;

	@Cacheable("oembed-cache")
	@Transactional(readOnly = true)
	@PreAuthorize( "@auth.hasRole('VIEWER')")
	@Timed(value = "jasper.service", extraTags = {"service", "oembed"}, histogram = true)
	public JsonNode get(Map<String, String> params) {
		var config = oembedProviders.getProvider(auth.getOrigin(), params.get("url"));
		if (config == null) return null;
		params.put("format", "json");
		try {
			return objectMapper.readTree(oembedClient.oembed(new URI(config.getUrl().replace("{format}", "json")), params));
		} catch (Exception e) {
			return null;
		}
	}

	@PreAuthorize("@auth.canAddTag('+plugin/oembed')")
	@Timed(value = "jasper.service", extraTags = {"service", "oembed"}, histogram = true)
	public void restoreDefaults() throws IOException {
		oembedProviders.defaults(auth.getOrigin());
	}
}
