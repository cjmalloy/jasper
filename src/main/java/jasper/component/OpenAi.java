package jasper.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.OpenAiApi;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.finetune.FineTuneRequest;
import com.theokanning.openai.service.OpenAiService;
import jasper.client.dto.RefDto;
import jasper.config.Props;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import retrofit2.Retrofit;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static com.theokanning.openai.service.OpenAiService.defaultClient;
import static com.theokanning.openai.service.OpenAiService.defaultObjectMapper;
import static com.theokanning.openai.service.OpenAiService.defaultRetrofit;
import static com.theokanning.openai.service.OpenAiService.execute;
import static jasper.repository.spec.QualifiedTag.selector;
import static org.apache.commons.lang3.StringUtils.isBlank;

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

	private AiConfig getConfig() {
		var key = refRepository.findAll(selector("_openai/key" + props.getLocalOrigin()).refSpec());
		if (key.isEmpty()) {
			throw new NotFoundException("requires openai api key");
		}
		var aiPlugin = pluginRepository.findByTagAndOrigin("+plugin/openai",  props.getLocalOrigin())
			.orElseThrow(() -> new NotFoundException("+plugin/openai"));
		var config = objectMapper.convertValue(aiPlugin.getConfig(), AiConfig.class);
		if (isBlank(config.fineTuning)) return config;

		ObjectMapper mapper = defaultObjectMapper();
		OkHttpClient client = defaultClient(key.get(0).getComment(), Duration.ofSeconds(200));
		Retrofit retrofit = defaultRetrofit(client, mapper);
		var api = retrofit.create(OpenAiApi.class);
		var service = new OpenAiService(api);
		var fileId = config.fileId;
//		if (fileId != null) {
//			try {
//				service.deleteFile(fileId);
//			} catch (Exception e) {
//				logger.warn("Deleting file {} failed.", fileId);
//			}
//			fileId = null;
//		}
		if (fileId == null) {
			RequestBody purposeBody = RequestBody.create(okhttp3.MultipartBody.FORM, "fine-tune");
			RequestBody fileBody = RequestBody.create(config.fineTuning, TEXT);
			MultipartBody.Part body = MultipartBody.Part.createFormData("file", "fine-tune", fileBody);
			fileId = execute(api.uploadFile(purposeBody, body)).getId();
			config.fileId = fileId;
		}
//		for (var f : service.listFiles()) {
//			if (!f.getId().equals(fileId)) {
//				try {
//					service.deleteFile(f.getId());
//				} catch (Exception e) {
//					logger.warn("Deleting file {} failed.", f.getId());
//				}
//			}
//		}
		var ftId = config.ftId;
//		if (ftId != null) {
//			try {
//				service.deleteFineTune(ftId);
//			} catch (Exception e) {
//				logger.warn("Deleting fine tune {} failed.", ftId);
//			}
//			ftId = null;
//		}
		if (ftId == null) {
			var fineTuneRequest = FineTuneRequest.builder()
				.model("davinci")
				.trainingFile(fileId)
				.build();
			ftId = service.createFineTune(fineTuneRequest).getId();
			config.ftId = ftId;
		}
//		for (var ft : service.listFineTunes()) {
//			if (!ft.getStatus().equals("cancelled") && !ft.getId().equals(ftId)) {
//				try {
//					service.deleteFineTune(ft.getId());
//				} catch (Exception e) {
//					logger.warn("Deleting fine tune {} failed.", ft.getId());
//				}
//			}
//		}
		var fineTunedModel = config.fineTunedModel;
		if (fineTunedModel == null) {
			var res = service.retrieveFineTune(ftId);
			if (res.getStatus().equals("cancelled")) {
				config.ftId = null;
			} else {
				if (res.getStatus().equals("succeeded")) {
					fineTunedModel = res.getFineTunedModel();
					config.fineTunedModel = fineTunedModel;
				}
			}
		}
		aiPlugin.setConfig(objectMapper.convertValue(config, JsonNode.class));
		pluginRepository.save(aiPlugin);
		return config;
	}


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


	public ChatCompletionResult chatCompletion(String systemPrompt, String prompt) {
		var key = refRepository.findAll(selector("_openai/key" + props.getLocalOrigin()).refSpec());
		if (key.isEmpty()) {
			throw new NotFoundException("requires openai api key");
		}
		var service = new OpenAiService(key.get(0).getComment(), Duration.ofSeconds(200));
		var completionRequest = ChatCompletionRequest.builder()
			.model("gpt-4")
			.maxTokens(4096)
			.messages(List.of(
				cm("system", systemPrompt),
				cm("user", prompt)
			))
			.build();
		try {
			return service.createChatCompletion(completionRequest);
		} catch (OpenAiHttpException e) {
			if ("context_length_exceeded".equals(e.code)) {
				completionRequest.setMaxTokens(400);
				try {
					return service.createChatCompletion(completionRequest);
				} catch (OpenAiHttpException second) {
					if ("context_length_exceeded".equals(second.code)) {
						completionRequest.setMaxTokens(20);
						try {
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

	public CompletionResult fineTunedCompletion(List<ChatMessage> messages) {
		var key = refRepository.findAll(selector("_openai/key" + props.getLocalOrigin()).refSpec());
		if (key.isEmpty()) {
			throw new NotFoundException("requires openai api key");
		}
		var service = new OpenAiService(key.get(0).getComment(), Duration.ofSeconds(200));
		var completionRequest = CompletionRequest.builder()
			.maxTokens(1024)
			.prompt(messages.stream().map(ChatMessage::getContent).collect(Collectors.joining("\n")))
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

	public ChatCompletionResult chat(String model, List<ChatMessage> messages) throws JsonProcessingException {
		var key = refRepository.findAll(selector("_openai/key" + props.getLocalOrigin()).refSpec());
		if (key.isEmpty()) {
			throw new NotFoundException("requires openai api key");
		}
		OpenAiService service = new OpenAiService(key.get(0).getComment(), Duration.ofSeconds(200));
		ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
			.maxTokens(4096)
			.messages(messages)
			.model("gpt-4")
			.build();
		try {
			return service.createChatCompletion(completionRequest);
		} catch (OpenAiHttpException e) {
			logger.error("context_length_exceeded {}", 4096);
			if ("context_length_exceeded".equals(e.code)) {
				try {
					completionRequest.setMaxTokens(400);
					return service.createChatCompletion(completionRequest);
				} catch (OpenAiHttpException second) {
					logger.error("context_length_exceeded {}", 400);
					if ("context_length_exceeded".equals(second.code)) {
						try {
							completionRequest.setMaxTokens(20);
							return service.createChatCompletion(completionRequest);
						} catch (OpenAiHttpException third) {
							logger.error("context_length_exceeded {}", 20);
							throw e;
						}
					}
					throw e;
				}
			}
			throw e;
		}
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
		public String model;
		public String systemPrompt;
		public String fineTuning;
		public String fileId;
		public String ftId;
		public String fineTunedModel;
	}
}
