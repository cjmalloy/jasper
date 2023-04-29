package jasper.component.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.Ingest;
import jasper.component.scheduler.Async;
import jasper.domain.Ref;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.service.ExtService;
import jasper.service.PluginService;
import jasper.service.RefService;
import jasper.service.TemplateService;
import jasper.service.UserService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

import static jasper.domain.proj.Tag.replySources;
import static jasper.domain.proj.Tag.replyTags;

@Profile("script")
@Component
public class Fetch extends Delta {
	private static final Logger logger = LoggerFactory.getLogger(Fetch.class);

	private final Async async;
	private final PluginRepository pluginRepository;
	private final ObjectMapper objectMapper;
	private final RefService refService;
	private final ExtService extService;
	private final PluginService pluginService;
	private final TemplateService templateService;
	private final UserService userService;

	public Fetch(Ingest ingest,
				 RefRepository refRepository,
				 Async async,
				 PluginRepository pluginRepository,
				 ObjectMapper objectMapper, RefService refService, ExtService extService, PluginService pluginService, TemplateService templateService, UserService userService) {
		super("+plugin/fetch", ingest, refRepository);
		this.async = async;
		this.pluginRepository = pluginRepository;
		this.objectMapper = objectMapper;
		this.refService = refService;
		this.extService = extService;
		this.pluginService = pluginService;
		this.templateService = templateService;
		this.userService = userService;
	}

	@PostConstruct
	void init() {
		async.addAsync("plugin/fetch", this);
	}

	@Override
	public DeltaReply transform(Ref ref, List<Ref> sources) {
		logger.debug("Running script {} ({})", ref.getTitle(), ref.getUrl());
		var fetch = objectMapper.convertValue(ref.getPlugins().with("plugin/fetch"), jasper.plugin.Fetch.class);
		var fetchPlugin = pluginRepository.findByTagAndOrigin("+plugin/fetch", ref.getOrigin())
			.orElseThrow(() -> new NotFoundException("+plugin/fetch"));
		var config = objectMapper.convertValue(fetchPlugin.getConfig(), FetchConfig.class);
		var response = new Ref();
		try {
			Page<?> page = switch (fetch.getType()) {
				case "ref" -> refService.page(fetch.getRefFilter(), PageRequest.of(fetch.getPage(), fetch.getSize()));
				case "ext" -> extService.page(fetch.getFilter(), PageRequest.of(fetch.getPage(), fetch.getSize()));
				case "plugin" -> pluginService.page(fetch.getFilter(), PageRequest.of(fetch.getPage(), fetch.getSize()));
				case "template" -> templateService.page(fetch.getTemplateFilter(), PageRequest.of(fetch.getPage(), fetch.getSize()));
				case "user" -> userService.page(fetch.getFilter(), PageRequest.of(fetch.getPage(), fetch.getSize()));
				default -> throw new NotFoundException("type");
			};
			response.setComment(objectMapper.writeValueAsString(fetch));
			response.setPlugin("+plugin/fetch", objectMapper.convertValue(page, JsonNode.class));
		} catch (Exception e) {
			response.setComment("Error fetching. " + e.getMessage());
			response.setUrl(fetch.getReplyUrl());
		}
		response.setOrigin(ref.getOrigin());
		response.setTags(new ArrayList<>(List.of("+plugin/fetch")));
		response.addTags(replyTags(ref));
		response.setSources(replySources(ref));
		return DeltaReply.of(response);
	}

	@Getter
	@Setter
	private static class FetchConfig {
	}
}
