package jasper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jasper.client.OembedClient;
import jasper.config.Props;
import jasper.errors.NotFoundException;
import jasper.plugin.Oembed;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class OembedService {
	private static final Logger logger = LoggerFactory.getLogger(OembedService.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	OembedClient oembedClient;

	@Autowired
	Auth auth;

	@Autowired
	Props props;

	@Autowired
	ObjectMapper objectMapper;

	@PreAuthorize("hasRole('VIEWER')")
	@Timed(value = "jasper.service", extraTags = {"service", "oembed"}, histogram = true)
	public JsonNode get(Map<String, String> params) throws URISyntaxException {
		var config = getProvider(params.get("url"));
		if (config == null) throw new NotFoundException("oembed");
		return oembedClient.oembed(new URI(config.getUrl()), params);
	}

	@PreAuthorize("hasRole('VIEWER')")
	@Timed(value = "jasper.service", extraTags = {"service", "oembed"}, histogram = true)
	public Oembed.Endpoints getProvider(String url) {
		for (var origin : props.getOembedOrigins()) {
			var providers = refRepository.findAll(
					auth.refReadSpec().and(RefFilter.builder()
						.origin(origin)
						.query("+plugin/oembed").build().spec())).stream()
				.map(r -> r.getPlugins().get("+plugin/oembed"))
				.map(p -> objectMapper.convertValue(p, Oembed.class)).toList();
			for (var p : providers) {
				for (var e : p.getEndpoints()) {
					if (e.getSchemes() == null ) continue;
					for (var s : e.getSchemes()) {
						if (url.matches(s.replace("*", ".*"))) {
							return e;
						}
					}
				}
			}
		}
		return null;
	}
}
