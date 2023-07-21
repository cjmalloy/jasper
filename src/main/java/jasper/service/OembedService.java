package jasper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jasper.client.OembedClient;
import jasper.component.OembedProviders;
import jasper.config.Props;
import jasper.plugin.Oembed;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
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
import java.util.regex.Pattern;

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

	@Cacheable("oembed")
	@Transactional(readOnly = true)
	@PreAuthorize("hasRole('VIEWER')")
	@Timed(value = "jasper.service", extraTags = {"service", "oembed"}, histogram = true)
	public JsonNode get(Map<String, String> params) {
		var config = getProvider(auth.getOrigin(), params.get("url"));
		if (config == null) return null;
		params.put("format", "json");
		try {
			return objectMapper.readTree(oembedClient.oembed(new URI(config.getUrl().replace("{format}", "json")), params));
		} catch (Exception e) {
			return null;
		}
	}

	@PreAuthorize("hasRole('MOD')")
	@Timed(value = "jasper.service", extraTags = {"service", "oembed"}, histogram = true)
	public void restoreDefaults() throws IOException {
		oembedProviders.defaults(auth.getOrigin());
	}

	@Cacheable("oembed-provider")
	@Transactional(readOnly = true)
	@PreAuthorize("hasRole('VIEWER')")
	@Timed(value = "jasper.service", extraTags = {"service", "oembed"}, histogram = true)
	public Oembed.Endpoints getProvider(String origin, String url) {
		var providers = refRepository.findAll(
				auth.refReadSpec().and(RefFilter.builder()
					.origin(origin)
					.query("+plugin/oembed").build().spec())).stream()
			.map(r -> r.getPlugins().get("+plugin/oembed"))
			.map(p -> objectMapper.convertValue(p, Oembed.class)).toList();
		for (var p : providers) {
			for (var e : p.getEndpoints()) {
				if (e.getSchemes() == null || e.getSchemes().isEmpty()) {
					if (url.startsWith(p.getProvider_url())) return e;
					continue;
				}
				for (var s : e.getSchemes()) {
					var regex = Pattern.quote(s).replace("*", "\\E.*\\Q");
					if (url.matches(regex)) return e;
				}
			}
		}
		return null;
	}
}
