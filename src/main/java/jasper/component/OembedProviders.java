package jasper.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.config.Props;
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

@Component
public class OembedProviders {
	private static final Logger logger = LoggerFactory.getLogger(OembedProviders.class);

	@Autowired
	Props props;

	@Autowired
	Ingest ingest;

	@Value("classpath:providers.json")
	Resource defaultProviders;

	@Autowired
	ObjectMapper objectMapper;

	public void defaults(String origin) throws IOException {
		create(origin, objectMapper.readValue(defaultProviders.getFile(), new TypeReference<List<Oembed>>() {}));
	}

	public void create(String origin, List<Oembed> providers) {
		logger.info("Restoring default oEmbed providers...");
		for (var p : providers) {
			var ref = new Ref();
			ref.setUrl(p.getProvider_url());
			ref.setTitle(p.getProvider_name());
			ref.setTags(new ArrayList<>(List.of("public", "internal", "+plugin/oembed")));
			var plugins = objectMapper.createObjectNode();
			plugins.set("+plugin/oembed", objectMapper.convertValue(p, JsonNode.class));
			ref.setPlugins(plugins);
			ref.setOrigin(origin);
			ingest.ingest(ref, false);
		}
		logger.info("Done restoring default oEmbed providers.");
	}
}
