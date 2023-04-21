package jasper.component;

import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import jasper.config.Props;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

import static jasper.repository.spec.QualifiedTag.selector;

@Component
public class OpenAi {
	private static final Logger logger = LoggerFactory.getLogger(OpenAi.class);

	@Autowired
	Props props;

	@Autowired
	RefRepository refRepository;

	public CompletionResult completion(String systemPrompt, String prompt) {
		var key = refRepository.findAll(selector("_openai/key" + props.getLocalOrigin()).refSpec());
		if (key.isEmpty()) {
			throw new NotFoundException("requires openai api key");
		}
		OpenAiService service = new OpenAiService(key.get(0).getComment(), Duration.ofSeconds(200));
		CompletionRequest completionRequest = CompletionRequest.builder()
			.maxTokens(1024)
			.prompt(systemPrompt + "\n\n" +
				"Prompt: " + prompt + "\n\n" +
				"Reply:")
			.model("text-davinci-003")
			.stop(List.of("Prompt:", "Reply:"))
			.build();
		try {
			return service.createCompletion(completionRequest);
		} catch (OpenAiHttpException e) {
			if ("context_length_exceeded".equals(e.code)) {
				completionRequest.setMaxTokens(400);
				try {
					return service.createCompletion(completionRequest);
				} catch (OpenAiHttpException second) {
					if ("context_length_exceeded".equals(second.code)) {
						completionRequest.setMaxTokens(20);
						try {
							return service.createCompletion(completionRequest);
						} catch (OpenAiHttpException third) {
							throw e;
						}
					}
					throw e;
				}
			}
			throw e;
		}
	}

	public ChatCompletionResult chat(List<ChatMessage> messages) {
		var key = refRepository.findAll(selector("_openai/key" + props.getLocalOrigin()).refSpec());
		if (key.isEmpty()) {
			throw new NotFoundException("requires openai api key");
		}
		OpenAiService service = new OpenAiService(key.get(0).getComment(), Duration.ofSeconds(200));
		ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
			.maxTokens(2048)
			.messages(messages)
			.model("gpt-3.5-turbo")
			.build();
		try {
			return service.createChatCompletion(completionRequest);
		} catch (OpenAiHttpException e) {
			if ("context_length_exceeded".equals(e.code)) {
				try {
					completionRequest.setMaxTokens(400);
					return service.createChatCompletion(completionRequest);
				} catch (OpenAiHttpException second) {
					if ("context_length_exceeded".equals(second.code)) {
						try {
							completionRequest.setMaxTokens(20);
							return service.createChatCompletion(completionRequest);
						} catch (OpenAiHttpException third) {
							throw e;
						}
					}
					throw e;
				}
			}
			throw e;
		}
	}

	public static ChatMessage cm(String role, String content) {
		var result = new ChatMessage();
		result.setRole(role);
		result.setContent(content);
		return result;
	}
}
