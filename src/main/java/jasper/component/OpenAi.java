package jasper.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.image.CreateImageRequest;
import com.theokanning.openai.image.ImageResult;
import com.theokanning.openai.service.OpenAiService;
import jasper.client.dto.RefDto;
import jasper.config.Props;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import okhttp3.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

import static jasper.repository.spec.QualifiedTag.selector;

@Profile("ai")
@Component
public class OpenAi {
	private static final Logger logger = LoggerFactory.getLogger(OpenAi.class);

	public static final MediaType TEXT = MediaType.parse("text/plain; charset=utf-8");

	@Autowired
	Props props;

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	ObjectMapper objectMapper;


	public CompletionResult completion(String systemPrompt, String prompt) {
		var key = refRepository.findAll(selector("_openai/key" + props.getLocalOrigin()).refSpec());
		if (key.isEmpty()) {
			throw new NotFoundException("requires openai api key");
		}
		var service = new OpenAiService(key.get(0).getComment(), Duration.ofSeconds(200));
		var completionRequest = CompletionRequest.builder()
			.model("text-davinci-003")
			.maxTokens(1024)
			.prompt(systemPrompt + "\n\n" +
				"Prompt: " + prompt + "\n\n" +
				"Reply:")
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

	public ChatCompletionResult chatCompletion(String prompt, AiConfig config) {
		var key = refRepository.findAll(selector("_openai/key" + props.getLocalOrigin()).refSpec());
		if (key.isEmpty()) {
			throw new NotFoundException("requires openai api key");
		}
		var service = new OpenAiService(key.get(0).getComment(), Duration.ofSeconds(200));
		var completionRequest = ChatCompletionRequest
			.builder()
			.model(config.model)
			.maxTokens(config.maxTokens)
			.messages(List.of(
				cm("system", config.systemPrompt),
				cm("user", prompt)
			))
			.build();
		return service.createChatCompletion(completionRequest);
	}

	public ImageResult dale(String prompt, DalleConfig config) {
		var key = refRepository.findAll(selector("_openai/key" + props.getLocalOrigin()).refSpec());
		if (key.isEmpty()) {
			throw new NotFoundException("requires openai api key");
		}
		var service = new OpenAiService(key.get(0).getComment(), Duration.ofSeconds(200));
		var imageRequest = CreateImageRequest.builder()
			.prompt(prompt)
			.responseFormat("b64_json")
			.model(config.model)
			.size(config.size)
			.quality(config.quality)
			.style(config.style)
			.build();
		return service.createImage(imageRequest);
	}

	public ChatCompletionResult chat(List<ChatMessage> messages, AiConfig config) {
		var key = refRepository.findAll(selector("_openai/key" + props.getLocalOrigin()).refSpec());
		if (key.isEmpty()) {
			throw new NotFoundException("requires openai api key");
		}
		OpenAiService service = new OpenAiService(key.get(0).getComment(), Duration.ofSeconds(200));
		ChatCompletionRequest completionRequest = ChatCompletionRequest
			.builder()
			.model(config.model)
			.maxTokens(config.maxTokens)
			.messages(messages)
			.build();
		return service.createChatCompletion(completionRequest);
	}

	public static ChatMessage cm(String origin, String role, String title, String content, ObjectMapper om) {
		var result = new ChatMessage();
		result.setRole(role);
		result.setContent(ref(origin, role, title, content, om));
		return result;
	}

	public static ChatMessage cm(String role, String content) {
		var result = new ChatMessage();
		result.setRole(role);
		result.setContent(content);
		return result;
	}

	public static String ref(String origin, String role, String title, String content, ObjectMapper om) {
		var result = new RefDto();
		result.setOrigin(origin);
		result.setTitle(title);
		result.setComment(content);
		if (role.equals("system")) {
			result.setUrl("system:instructions");
			result.setTags(List.of("system", "internal", "notes", "+plugin/openai"));
		} else {
			result.setUrl("system:user-instructions");
			result.setTags(List.of("dm", "internal", "plugin/thread"));
		}
		try {
			return om.writeValueAsString(result);
		} catch (JsonProcessingException e) {
			logger.error("Cannot write content to Ref {}", content, e);
			throw new RuntimeException(e);
		}
	}

	public static class AiConfig {
		public String model = "gpt-4-1106-preview";
		public List<String> fallback;
		public int maxTokens = 4096;
		public int maxContext = 7;
		public String systemPrompt;
	}

	public static class DalleConfig {
		public String size = "1024x1024";
		public String model = "dall-e-3";
		public String quality = "hd";
		public String style = "natural";
	}

}
