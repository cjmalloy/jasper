package jasper.component.delta;

import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.Ingest;
import jasper.component.scheduler.Async;
import jasper.domain.Ref;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jasper.domain.proj.Tag.replySources;
import static jasper.domain.proj.Tag.replyTags;

@Profile("script")
@Component
public class Script extends Delta {
	private static final Logger logger = LoggerFactory.getLogger(Script.class);

	private final Async async;
	private final PluginRepository pluginRepository;
	private final ObjectMapper objectMapper;

	public Script(Ingest ingest, RefRepository refRepository, Async async, PluginRepository pluginRepository, ObjectMapper objectMapper) {
		super("+plugin/delta", ingest, refRepository);
		this.async = async;
		this.pluginRepository = pluginRepository;
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	void init() {
		async.addAsync("plugin/delta", this);
	}

	@Override
	public Delta.DeltaReply transform(Ref ref, List<Ref> sources) {
		logger.debug("Running script {} ({})", ref.getTitle(), ref.getUrl());
		var deltaPlugin = pluginRepository.findByTagAndOrigin("+plugin/delta", ref.getOrigin())
			.orElseThrow(() -> new NotFoundException("+plugin/delta"));
		var config = objectMapper.convertValue(deltaPlugin.getConfig(), DeltaConfig.class);
		var response = new Ref();
		try {
			// run script
		} catch (Exception e) {
			response.setComment("Error creating the summary. " + e.getMessage());
			response.setUrl("internal:" + UUID.randomUUID());
		}
		response.setOrigin(ref.getOrigin());
		response.setTags(new ArrayList<>(List.of("+plugin/delta")));
		response.addTags(replyTags(ref));
		response.setSources(replySources(ref));
		return DeltaReply.of(response);
	}

	@Getter
	@Setter
	private static class DeltaConfig {
	}
}
