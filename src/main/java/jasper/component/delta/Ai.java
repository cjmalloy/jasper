package jasper.component.delta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatMessage;
import jasper.component.Ingest;
import jasper.component.OpenAi;
import jasper.component.scheduler.Async;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class Ai extends Delta {
	private static final Logger logger = LoggerFactory.getLogger(Ai.class);

	private final Async async;
	private final OpenAi openAi;
	private final PluginRepository pluginRepository;
	private final TemplateRepository templateRepository;
	private final ObjectMapper objectMapper;
	private final RefMapper refMapper;

	public Ai(Ingest ingest, RefRepository refRepository, Async async, OpenAi openAi, PluginRepository pluginRepository, TemplateRepository templateRepository, ObjectMapper objectMapper, RefMapper refMapper) {
		super("+plugin/openai", ingest, refRepository);
		this.async = async;
		this.openAi = openAi;
		this.pluginRepository = pluginRepository;
		this.templateRepository = templateRepository;
		this.objectMapper = objectMapper;
		this.refMapper = refMapper;
	}

	@PostConstruct
	void init() {
		async.addAsync("plugin/inbox/ai", this);
	}

	@Override
	public Delta.DeltaReply transform(Ref ref, List<Ref> sources) {
		logger.debug("AI replying to {} ({})", ref.getTitle(), ref.getUrl());
		var author = ref.getTags().stream().filter(User::isUser).findFirst().orElse(null);
		var aiPlugin = pluginRepository.findByTagAndOrigin("+plugin/openai", ref.getOrigin())
			.orElseThrow(() -> new NotFoundException("+plugin/openai"));
		var config = objectMapper.convertValue(aiPlugin.getConfig(), OpenAi.AiConfig.class);
		var sample = refMapper.domainToDto(ref);
		String pluginString = null;
		try {
			pluginString = objectMapper.writeValueAsString(ref.getPlugins());
		} catch (JsonProcessingException e) {
			logger.trace("Could not parse plugins ", e);
		}
		if (pluginString == null || pluginString.length() > 2000 || pluginString.contains(";base64,")) {
			sample.setPlugins(null);
		}
		var plugins = "{" + pluginRepository.findAll().stream().map(p ->
			p.getTag() + ": " +
			p.getConfig().get("description")).collect(Collectors.joining(", ")) +
		"}";
		var templates = "{" + templateRepository.findAll().stream().map(t ->
			t.getTag() + ": " +
			t.getConfig().get("description")).collect(Collectors.joining(", ")) +
		"}";
		var instructions = """
				Include your response as the comment field of a Ref.
				Only reply with pure JSON. Do not include any text outside of the JSON Ref.
				You may supply any title that is appropriate, but the usual is to prefix "Re:" to
				the title of the source Ref (unless it is already prefixed, don't double prefix like "Re: Re:")
				For example, in response to:
				```json
				{
				  "url": "comment:116b2d94-aea3-4c4e-8c49-8eba5c45023c",
				  "origin": ""
				  "title": "Say Hi!",
				  "tags": [
				    "public",
				    "+user/chris",
				    "plugin/poll"
				  ],
				  "published": "2023-04-22T13:24:06.786Z",
				  "modified": "2023-04-22T13:24:06.895197Z"
				  "created": "2023-04-22T13:24:06.895197Z"
				}
				You could respond:
				```json
				{
					"url": "ai:f40b2a61-c9e1-4201-9a91-e00cf03f19d8",
					"origin": "",
					"sources": ["comment:116b2d94-aea3-4c4e-8c49-8eba5c45023c"],
					"title" "Re: Say Hi",
					"comment": "Hi!",
					"tags": [
						"public",
						"+plugin/ai",
						"plugin/inbox/user/chris"
					]
				}
				```
				All date times are ISO format Zulu time like: "2023-04-22T20:38:19.480464Z"
				Always add the "+plugin/ai" tag, as that is your signature.
				Never include a tag like "+user/chris", as that is impersonation.
				You may only use public tags (starting with a lowercase letter or number) and your protected signature tag: +plugin/ai
				Only add the "plugin/ai" tag to trigger an AI response to your comment as well (spawn an new
				agent with your Ref as the prompt).
				Include your response as the comment field of a Ref.
				Never include metadata, only Jasper creates metadata asynchronously.
				Only reply with pure JSON.
				Do not include any text outside of the JSON Ref.
				The first character of your reply should be {.
				Only output valid JSON.
			""";
		var fallback = new Ref();
		var resArray = Delta.DeltaReply.of(fallback);
		try {
			var messages = new ArrayList<>(List.of(
				cm(ref.getOrigin(), "system", "System Prompt", config.systemPrompt, objectMapper)
			));
			if (author != null) {
				messages.add(cm(ref.getOrigin(), "user", "Explanation of signature tag", "The author of this ref is " + author + ". ", objectMapper));
			}
			if (author == null) {
				messages.add(cm(ref.getOrigin(), "system", "Spawning Agent Prompt", "You are following up on a previous ref to further a line of inquiry, " +
					"suggest the next course of action, or resolve the matter in your reply.", objectMapper));
			}
			messages.add(cm(ref.getOrigin(), "system", "Installed Plugins List", plugins, objectMapper));
			messages.add(cm(ref.getOrigin(), "system", "Installed Templates List", templates, objectMapper));
			messages.add(cm("user", objectMapper.writeValueAsString(sample)));
			messages.add(cm(ref.getOrigin(), "system", "Output format instructions", instructions, objectMapper));
			var reply = "";
			Object msg = null;
			if (config.fineTuning == null) {
				var res = openAi.chat(messages);
				msg = res;
				reply = res.getChoices().stream().map(ChatCompletionChoice::getMessage).map(ChatMessage::getContent).collect(Collectors.joining("\n\n"));
				fallback.setUrl("ai:" + res.getId());
				logger.trace("Reply: " + reply);
			} else {
				var res = openAi.fineTunedCompletion(messages);
				msg = res;
				reply = res.getChoices().stream().map(CompletionChoice::getText).collect(Collectors.joining("\n\n"));
				fallback.setUrl("ai:" + res.getId());
				logger.trace("Reply: " + reply);
			}
			fallback.setComment(reply);
			fallback.setPlugin("+plugin/ai", objectMapper.convertValue(msg, JsonNode.class));
		} catch (Exception e) {
			fallback.setComment("Error invoking AI. " + e.getMessage());
			fallback.setUrl("internal:" + UUID.randomUUID());
		}
		Exception ex = null;
		try {
			resArray = objectMapper.readValue(fallback.getComment(), new TypeReference<Delta.DeltaReply>(){});
			resArray.res().setUrl(fallback.getUrl());
			resArray.res().setPlugin("+plugin/openai", fallback.getPlugins().get("+plugin/openai"));
		} catch (Exception e) {
			logger.warn("Falling back: AI did not reply with JSON.");
			logger.warn(fallback.getComment(), ex);
			fallback.setTags(new ArrayList<>(List.of("plugin/debug", "+plugin/openai", "plugin/latex")));
		}
		return resArray;
	}
}
