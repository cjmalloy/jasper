package jasper.component.delta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatMessage;
import jasper.component.Ingest;
import jasper.component.OpenAi;
import jasper.component.scheduler.Async;
import jasper.domain.Ref;
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

	@PostConstruct
	void init() {
		async.addAsync("plugin/ai", this);
	}

	@Override
	public void run(Ref ref) throws JsonProcessingException {
		if (ref.hasPluginResponse("+plugin/ai")) return;
		var author = ref.getTags().stream().filter(this::isUser).findFirst().orElse(null);
		if (author == null) {
			logger.warn("plugin/api chat requires author");
			return;
		}
		var aiPlugin = pluginRepository.findByTagAndOrigin("+plugin/ai", ref.getOrigin())
			.orElseThrow(() -> new NotFoundException("+plugin/ai"));
		var config = objectMapper.convertValue(aiPlugin.getConfig(), AiConfig.class);
		var response = new Ref();
		try {
			var messages = List.of(
				cm("system", config.getSystemPrompt()),
				cm("user", String.join("\n\n",
					config.getAuthorPrompt().replace("{author}", author),
					"Title: " + ref.getTitle(),
					"Tags: " + String.join(", ", ref.getTags()),
					ref.getComment(),
					config.getInstructionPrompt()))
			);
			var res = openAi.chat(messages);
			response.setComment(res.getChoices().stream().map(ChatCompletionChoice::getMessage).map(ChatMessage::getContent).collect(Collectors.joining("\n\n")));
			response.setUrl("ai:" + res.getId());
		} catch (Exception e) {
			response.setComment("Error invoking AI. " + e.getMessage());
			response.setUrl("internal:" + UUID.randomUUID());
		}
		response.setOrigin(ref.getOrigin());
		response.setSources(List.of(ref.getUrl()));
		response.setTags(new ArrayList<>(List.of("public", "ai", "+plugin/ai", "internal", "plugin/comment", "plugin/inbox/" + author.substring(1))));
		ingest.ingest(response, false);
	}

	private boolean isUser(String t) {
		return t.startsWith("+user") ||
			t.startsWith("_user") ||
			t.startsWith("+user/") ||
			t.startsWith("_user/");
	}

	@Getter
	@Setter
	private static class AiConfig {
		private String systemPrompt;
		private String authorPrompt;
		private String instructionPrompt;
	}
}
