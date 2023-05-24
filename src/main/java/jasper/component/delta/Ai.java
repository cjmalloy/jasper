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
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.Tag;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import jasper.repository.TemplateRepository;
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
	TemplateRepository templateRepository;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	RefMapper refMapper;

	@PostConstruct
	void init() {
		async.addAsyncResponse("plugin/inbox/ai", this);
	}

	@Override
	public String signature() {
		return "+plugin/openai";
	}

	@Override
	public void run(Ref ref) throws JsonProcessingException {
		logger.debug("AI replying to {} ({})", ref.getTitle(), ref.getUrl());
		var author = ref.getTags().stream().filter(User::isUser).findFirst().orElse(null);
		var aiPlugin = pluginRepository.findByTagAndOrigin("+plugin/openai", ref.getOrigin())
			.orElseThrow(() -> new NotFoundException("+plugin/openai"));
		var config = objectMapper.convertValue(aiPlugin.getConfig(), OpenAi.AiConfig.class);
		var sample = refMapper.domainToDto(ref);
		var pluginString = objectMapper.writeValueAsString(ref.getPlugins());
		if (pluginString.length() > 2000 || pluginString.contains(";base64,")) {
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
				"plugin/inbox/ai"
			  ],
			  "published": "2023-04-22T13:24:06.786Z",
			  "modified": "2023-04-22T13:24:06.895197Z"
			  "created": "2023-04-22T13:24:06.895197Z"
			}
			You could respond:
			```json
			{
				"ref":[{
					"url": "ai:f40b2a61-c9e1-4201-9a91-e00cf03f19d8",
					"origin": "",
					"sources": ["comment:116b2d94-aea3-4c4e-8c49-8eba5c45023c"],
					"title" "Re: Say Hi",
					"comment": "Hi!",
					"tags": [
						"public",
						"ai",
						"+plugin/openai",
						"plugin/inbox/user/chris"
					]
				}],
				"ext":[],
				"plugin":[],
				"template":[],
				"user":[]
				}
			```
			Also include any other entities (refs, exts, plugins, templates, and users) in your response and they
			will be created. However, the first Ref should be considered your response and is the only required response.
			All date times are ISO format Zulu time like: "2023-04-22T20:38:19.480464Z"
			Always add the "+plugin/openai" tag, as that is your signature.
			Never include a tag like "+user/chris", as that is impersonation.
			You may only use public tags (starting with a lowercase letter or number) and your protected signature tag: +plugin/openai
			Only add the "plugin/openai" tag to trigger an AI response to your comment as well (spawn an new
			agent with your Ref as the prompt).
			Include your response as the comment field of a Ref.
			Never include metadata, only Jasper creates metadata asynchronously.
			Only reply with pure JSON.
			Do not include any text outside of the JSON Ref.
			The first character of your reply should be {.
			Only output valid JSON.
		""";
		var response = new Ref();
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
			if (config.model.equals("gpt-4")) {
				var res = openAi.chat("gpt-4", messages);
				msg = res;
				reply = res.getChoices().stream().map(ChatCompletionChoice::getMessage).map(ChatMessage::getContent).collect(Collectors.joining("\n\n"));
				response.setUrl("ai:" + res.getId());
				logger.trace("Reply: " + reply);
			} else if (config.model.equals("text-davinci-003")) {
				var res = openAi.fineTunedCompletion(messages);
				msg = res;
				reply = res.getChoices().stream().map(CompletionChoice::getText).collect(Collectors.joining("\n\n"));
				response.setUrl("ai:" + res.getId());
				logger.trace("Reply: " + reply);
			}
			response.setComment(reply);
			response.setPlugin("+plugin/openai", objectMapper.convertValue(msg, JsonNode.class));
		} catch (Exception e) {
			response.setComment("Error invoking AI. " + e.getMessage());
			response.setUrl("internal:" + UUID.randomUUID());
		}
		List<Ref> refArray = List.of(response);
		Exception ex = null;
		try {
			try {
				// Try getting single Ref
				refArray = List.of(objectMapper.readValue(response.getComment(), new TypeReference<Ref>(){}));
			} catch (Exception e) {
				ex = e;
				// Try with array
				refArray = objectMapper.readValue(response.getComment(), new TypeReference<List<Ref>>() {});
			}
			refArray.get(0).setUrl(response.getUrl());
			refArray.get(0).setPlugin("+plugin/openai", response.getPlugins().get("+plugin/openai"));
		} catch (Exception e) {
			logger.warn("Falling back: AI did not reply with JSON.");
			logger.warn(response.getComment(), ex);
			response.setTags(new ArrayList<>(List.of("plugin/debug", "ai", "+plugin/openai", "plugin/latex")));
		}
		response = refArray.get(0);
		var tags = new ArrayList<String>();
		if (ref.getTags().contains("public")) tags.add("public");
		if (ref.getTags().contains("internal")) tags.add("internal");
		if (ref.getTags().contains("dm")) tags.add("dm");
		if (ref.getTags().contains("dm")) tags.add("plugin/thread");
		if (ref.getTags().contains("plugin/email")) tags.add("plugin/email");
		if (ref.getTags().contains("plugin/email")) tags.add("plugin/thread");
		if (ref.getTags().contains("plugin/comment")) tags.add("plugin/comment");
		if (ref.getTags().contains("plugin/comment")) tags.add("plugin/thread");
		if (ref.getTags().contains("plugin/thread")) tags.add("plugin/thread");
		if (author != null) tags.add("plugin/inbox/" + author.substring(1));
		for (var t : ref.getTags()) {
			if (t.startsWith("plugin/inbox/") || t.startsWith("plugin/outbox/")) {
				tags.add(t);
			}
		}
		response.addTags(tags);
		response.getTags().remove("plugin/inbox/ai");
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
		for (var aiReply : refArray) {
			aiReply.setOrigin(ref.getOrigin());
			if (aiReply.getTags() != null) {
				aiReply.setTags(new ArrayList<>(aiReply.getTags().stream().filter(
					t -> t.matches(Tag.REGEX) && (t.equals("+plugin/openai") || !t.startsWith("+") && !t.startsWith("_"))
				).toList()));
			}

			ingest.ingest(aiReply, false);
			logger.debug("AI reply sent ({})", aiReply.getUrl());
		}
	}

	@Getter
	@Setter
	private static class AiReply {
		private Ref[] ref;
		private Ext[] ext;
		private Plugin[] plugin;
		private Template[] template;
		private User[] user;
	}
}
