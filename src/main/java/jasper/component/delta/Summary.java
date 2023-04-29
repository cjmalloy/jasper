package jasper.component.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.CompletionChoice;
import jasper.component.Ingest;
import jasper.component.OpenAi;
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
import java.util.stream.Collectors;

@Profile("ai")
@Component
public class Summary extends Delta {
	private static final Logger logger = LoggerFactory.getLogger(Summary.class);

	private final Async async;
	private final OpenAi openAi;
	private final PluginRepository pluginRepository;
	private final ObjectMapper objectMapper;

	public Summary(Ingest ingest, RefRepository refRepository, Async async, OpenAi openAi, PluginRepository pluginRepository, ObjectMapper objectMapper) {
		super("+plugin/inbox/summary", ingest, refRepository);
		this.async = async;
		this.openAi = openAi;
		this.pluginRepository = pluginRepository;
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	void init() {
		async.addAsync("plugin/inbox/summary", this);
	}

	@Override
	public Delta.DeltaReply transform(Ref ref, List<Ref> sources) {
		logger.debug("AI summarizing {} ({})", ref.getTitle(), ref.getUrl());
		var summaryPlugin = pluginRepository.findByTagAndOrigin("+plugin/summary", ref.getOrigin())
			.orElseThrow(() -> new NotFoundException("+plugin/summary"));
		var config = objectMapper.convertValue(summaryPlugin.getConfig(), SummaryConfig.class);
		var response = new Ref();
		try {
			var res = openAi.completion("", String.join("\n\n",
				config.getSystemPrompt(),
				"Title: " + ref.getTitle(),
				"Tags: " + String.join(", ", ref.getTags()),
				ref.getComment()));
			response.setComment(res.getChoices().stream().map(CompletionChoice::getText).collect(Collectors.joining("\n\n")));
			response.setUrl("ai:" + res.getId());
			response.setPlugin("+plugin/summary", objectMapper.convertValue(res, JsonNode.class));
		} catch (Exception e) {
			response.setComment("Error creating the summary. " + e.getMessage());
			response.setUrl("internal:" + UUID.randomUUID());
		}
		var title = ref.getTitle();
		if (!title.startsWith(config.getTitlePrefix())) title = config.titlePrefix + title;
		response.setTitle(title);
		response.setOrigin(ref.getOrigin());
		response.setTags(new ArrayList<>(List.of("+plugin/summary")));
		return DeltaReply.of(response);
	}

	@Getter
	@Setter
	private static class SummaryConfig {
		private String titlePrefix;
		private String systemPrompt;
	}
}
