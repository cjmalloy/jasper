package jasper.component.delta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatMessage;
import jasper.component.Ingest;
import jasper.component.OpenAi;
import jasper.component.scheduler.Async;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static jasper.component.OpenAi.cm;

@Profile("ai")
@Component
public class Ai implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(Ai.class);

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

	@Autowired
	RefMapper refMapper;

	@PostConstruct
	void init() {
		async.addAsync("plugin/ai", this);
	}

	@Override
	public void run(Ref ref) throws JsonProcessingException {
		if (ref.hasPluginResponse("+plugin/ai")) return;
		var author = ref.getTags().stream().filter(User::isUser).findFirst().orElse(null);
		var aiPlugin = pluginRepository.findByTagAndOrigin("+plugin/ai", ref.getOrigin())
			.orElseThrow(() -> new NotFoundException("+plugin/ai"));
		var config = objectMapper.convertValue(aiPlugin.getConfig(), AiConfig.class);
		var response = new Ref();
		try {
			var messages = new ArrayList<>(List.of(
				cm("system", config.getSystemPrompt()),
				cm("user", objectMapper.writeValueAsString(refMapper.domainToDto(ref)))
			));
			var res = openAi.chat(messages);
			response.setComment(res.getChoices().stream().map(ChatCompletionChoice::getMessage).map(ChatMessage::getContent).collect(Collectors.joining("\n\n")));
			response.setUrl("ai:" + res.getId());
		} catch (Exception e) {
			response.setComment("Error invoking AI. " + e.getMessage());
			response.setUrl("internal:" + UUID.randomUUID());
		}
		var title = ref.getTitle();
		if (!title.startsWith(config.getTitlePrefix())) title = config.titlePrefix + title;
		response.setTitle(title);
		response.setOrigin(ref.getOrigin());
		response.setTags(new ArrayList<>(List.of("+plugin/ai", "plugin/latex")));
		var tags = new ArrayList<String>();
		if (ref.getTags().contains("public")) tags.add("public");
		if (ref.getTags().contains("internal")) tags.add("internal");
		if (ref.getTags().contains("dm")) tags.add("dm");
		if (ref.getTags().contains("dm")) tags.add("plugin/thread");
		if (ref.getTags().contains("plugin/email")) tags.add("plugin/email");
		if (ref.getTags().contains("plugin/email")) tags.add("plugin/thread");
		if (ref.getTags().contains("plugin/comment")) tags.add("plugin/comment");
		if (ref.getTags().contains("plugin/comment")) tags.add("plugin/thread");
		if (author != null) tags.add("plugin/inbox/" + author.substring(1));
		for (var t : ref.getTags()) {
			if (t.startsWith("plugin/inbox/") || t.startsWith("plugin/outbox/")) {
				tags.add(t);
			}
		}
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
		ingest.ingest(response, false);
	}

	@Getter
	@Setter
	private static class AiConfig {
		private String titlePrefix;
		private String systemPrompt;
		private String authorPrompt;
	}
}
