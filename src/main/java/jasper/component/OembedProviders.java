package jasper.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.domain.Ref;
import jasper.plugin.Oembed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static jasper.domain.proj.HasOrigin.formatOrigin;

@Component
public class OembedProviders {
	private static final Logger logger = LoggerFactory.getLogger(OembedProviders.class);

	@Autowired
	Ingest ingest;

	@Autowired
	ConfigCache configs;

	@Value("classpath:providers.json")
	Resource defaultProviders;

	@Autowired
	ObjectMapper objectMapper;

	public void defaults(String origin) throws IOException {
		create(origin, objectMapper.readValue(defaultProviders.getFile(), new TypeReference<List<Oembed>>() {}));
	}

	public void create(String origin, List<Oembed> providers) {
		logger.info("Restoring default oEmbed providers... (\"{}\")", formatOrigin(origin));
		for (var p : providers) {
			var ref = new Ref();
			ref.setUrl(p.getProvider_url());
			ref.setTitle(p.getProvider_name());
			ref.setTags(new ArrayList<>(List.of("public", "internal", "+plugin/oembed")));
			var plugins = objectMapper.createObjectNode();
			plugins.set("+plugin/oembed", objectMapper.convertValue(p, JsonNode.class));
			ref.setPlugins(plugins);
			ref.setOrigin(origin);
			ingest.push(ref, null);
		}
		logger.info("Done restoring default oEmbed providers.");
	}

	public Oembed.Endpoints getProvider(String origin, String url) {
		var providers = configs.getAllConfigs(origin, "+plugin/embed", Oembed.class);
		for (var p : providers) {
			if (p == null) continue;
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
