package jasper.component.delta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.apache.commons.lang3.StringUtils.isBlank;

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
		async.addAsync("plugin/ai", this);
	}

	@Override
	public void run(Ref ref) throws JsonProcessingException {
		if (ref.hasPluginResponse("+plugin/ai")) return;
		logger.debug("AI replying to {} ({})", ref.getTitle(), ref.getUrl());
		var author = ref.getTags().stream().filter(User::isUser).findFirst().orElse(null);
		var aiPlugin = pluginRepository.findByTagAndOrigin("+plugin/ai", ref.getOrigin())
			.orElseThrow(() -> new NotFoundException("+plugin/ai"));
		var config = objectMapper.convertValue(aiPlugin.getConfig(), AiConfig.class);
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
				Always add the "+plugin/ai" tag, as that is your signature.
				Never include a tag like "+user/chris", as that is impersonation.
				You may only use public tags (starting with a lowercase letter or number) and your protected signature tag: +plugin/ai
				Only add the "plugin/ai" tag to trigger an AI response to your comment as well (spawn an new
				agent with your Ref as the prompt).
				Include your response as the comment field of a Ref.
				Only reply with pure JSON.
				Do not include any text outside of the JSON Ref.
				The first character of your reply should be {.
				Only output valid JSON.
			""";
		var response = new Ref();
		try {
			var messages = new ArrayList<>(List.of(
				cm("system", config.getSystemPrompt())
			));
			if (author != null) {
				messages.add(cm("user", "The author of this ref is " + author + ". "));
			}
			if (author == null) {
				messages.add(cm("system", "You are following up on a previous ref to further a line of inquiry, " +
					"suggest the next course of action, or resolve the matter in your reply."));
			}
			messages.add(cm("system", "plugins: " + plugins));
			messages.add(cm("system", "templates: " + templates));
			messages.add(cm("system", "examples: " + examples));
			messages.add(cm("user", objectMapper.writeValueAsString(sample)));
			messages.add(cm("system", instructions));
			messages.add(cm(ref.getOrigin(), "system", "Output format instructions", instructions, objectMapper));
			var reply = "";
			Object msg = null;
			if (config.fineTuning == null) {
				var res = openAi.chat(messages);
				msg = res;
				reply = res.getChoices().stream().map(ChatCompletionChoice::getMessage).map(ChatMessage::getContent).collect(Collectors.joining("\n\n"));
				response.setUrl("ai:" + res.getId());
				logger.trace("Reply: " + reply);
			} else {
				var res = openAi.fineTunedCompletion(messages);
				msg = res;
				reply = res.getChoices().stream().map(CompletionChoice::getText).collect(Collectors.joining("\n\n"));
				response.setUrl("ai:" + res.getId());
				logger.trace("Reply: " + reply);
			}
			response.setComment(reply);
			response.setPlugin("+plugin/ai", objectMapper.convertValue(msg, JsonNode.class));
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
			refArray.get(0).setPlugin("+plugin/ai", response.getPlugins().get("+plugin/ai"));
		} catch (Exception e) {
			logger.warn("Falling back: AI did not reply with JSON.");
			logger.warn(response.getComment(), ex);
			response.setTags(new ArrayList<>(List.of("plugin/debug", "+plugin/ai", "plugin/latex")));
		}
		response = refArray.get(0);
		if (isBlank(response.getTitle())) {
			var title = ref.getTitle();
			if (!title.startsWith(config.getTitlePrefix())) title = config.titlePrefix + title;
			response.setTitle(title);
		}
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
					t -> t.matches(Tag.REGEX) && (t.equals("+plugin/ai") || !t.startsWith("+") && !t.startsWith("_"))
				).toList()));
			}

			ingest.ingest(aiReply, false);
			logger.debug("AI reply sent ({})", aiReply.getUrl());
		}
	}

	@Getter
	@Setter
	private static class AiConfig {
		private String titlePrefix;
		private String systemPrompt;
		private String authorPrompt;
	}
}
