package jasper.component.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatMessage;
import jasper.component.Ingest;
import jasper.component.OpenAi;
import jasper.component.OpenAi.AiConfig;
import jasper.component.scheduler.Async;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

@Profile("ai")
@Component
public class Summary implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(Summary.class);

	@Autowired
	Async async;

	@Autowired
	Ingest ingest;

	@Autowired
	OpenAi openAi;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	ObjectMapper objectMapper;

	@PostConstruct
	void init() {
		async.addAsyncResponse("plugin/summary", this);
	}

	@Override
	public String signature() {
		return "+plugin/summary";
	}

	@Override
	public void run(Ref ref) {
		logger.debug("AI summarizing {} ({})", ref.getTitle(), ref.getUrl());
		var author = ref.getTags().stream().filter(User::isUser).findFirst().orElse(null);
		var summaryPlugin = pluginRepository.findByTagAndOrigin("+plugin/summary", ref.getOrigin())
			.orElseThrow(() -> new NotFoundException("+plugin/summary"));
		var config = objectMapper.convertValue(summaryPlugin.getConfig(), SummaryConfig.class);
		var response = new Ref();
		try {
			var res = openAi.chatCompletion(String.join("\n\n",
				"Title: " + ref.getTitle(),
				"Tags: " + String.join(", ", ref.getTags()),
				ref.getComment()), config);
			response.setComment(res.getChoices().stream()
				.map(ChatCompletionChoice::getMessage)
				.map(ChatMessage::getContent)
				.collect(Collectors.joining("\n\n")));
			response.setUrl("ai:" + res.getId());
			response.setPlugin("+plugin/summary", objectMapper.convertValue(res.getUsage(), JsonNode.class));
		} catch (Exception e) {
			response.setComment("Error creating the summary. " + e.getMessage());
			response.setUrl("internal:" + UUID.randomUUID());
		}
		var title = ref.getTitle();
		if (!title.startsWith(config.titlePrefix)) title = config.titlePrefix + title;
		response.setTitle(title);
		response.setOrigin(ref.getOrigin());
		var tags = new ArrayList<String>();
		if (ref.getTags().contains("public")) tags.add("public");
		if (ref.getTags().contains("internal")) tags.add("internal");
		if (ref.getTags().contains("dm")) tags.add("dm");
		if (ref.getTags().contains("dm")) tags.add("internal");
		if (ref.getTags().contains("dm")) tags.add("plugin/thread");
		if (ref.getTags().contains("internal")) tags.add("internal");
		if (ref.getTags().contains("plugin/comment")) tags.add("internal");
		if (ref.getTags().contains("plugin/comment")) tags.add("plugin/comment");
		if (ref.getTags().contains("plugin/comment")) tags.add("plugin/thread");
		if (author != null) tags.add("plugin/inbox/" + author.substring(1));
		for (var t : ref.getTags()) {
			if (t.startsWith("plugin/inbox/") || t.startsWith("plugin/outbox/")) {
				tags.add(t);
			}
		}
		tags.add("+plugin/summary");
		tags.remove("plugin/inbox/ai");
		tags.remove("plugin/inbox/summary");
		response.addTags(tags);
		var sources = new ArrayList<>(List.of(ref.getUrl()));
		if (response.getTags().contains("plugin/thread")) {
			// Add top comment source
			if (ref.getSources() != null && ref.getSources().size() > 0) {
				if (ref.getSources().size() > 1) {
					sources.add(ref.getSources().get(1));
				} else {
					sources.add(ref.getSources().get(0));
				}
			}
		}
		response.setSources(sources);
		ingest.create(response, false);
	}

	private static class SummaryConfig extends AiConfig {
		public String titlePrefix;
	}
}
