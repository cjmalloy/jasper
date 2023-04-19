package jasper.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.plugin.Oembed;
import jasper.repository.RefRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;

@Component
public class OembedProviders {

	@Autowired
	Props props;

	@Autowired
	RefRepository refRepository;

	@Value("classpath:providers.json")
	Resource defaultProviders;

	@Autowired
	ObjectMapper objectMapper;

	@PostConstruct
	public void init() throws IOException {
		create(objectMapper.readValue(defaultProviders.getFile(), new TypeReference<List<Oembed>>() {}));
	}

	public void create(List<Oembed> providers) {
		for (var p : providers) {
			var ref = new Ref();
			ref.setUrl(p.getProvider_url());
			ref.setOrigin(props.getLocalOrigin());
			ref.setTitle(p.getProvider_name());
			ref.setTags(List.of("public", "internal", "+plugin/oembed"));
			var plugins = objectMapper.createObjectNode();
			plugins.set("+plugin/oembed", objectMapper.convertValue(p, JsonNode.class));
			ref.setPlugins(plugins);
			refRepository.save(ref);
		}
	}
}
