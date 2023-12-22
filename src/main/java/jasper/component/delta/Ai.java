package jasper.component.delta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.messages.MessageContent;
import com.theokanning.openai.messages.MessageRequest;
import com.theokanning.openai.messages.content.Text;
import jasper.component.Ingest;
import jasper.component.OpenAi;
import jasper.component.scheduler.Async;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.Tag;
import jasper.errors.NotFoundException;
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.service.dto.RefDto;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static jasper.component.OpenAi.m;
import static jasper.component.OpenAi.ref;
import static jasper.repository.spec.RefSpec.hasInternalResponse;
import static jasper.repository.spec.RefSpec.hasResponse;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.data.domain.Sort.by;

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
	RefRepository refRepository;

	@Autowired
	ExtRepository extRepository;

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
		// TODO: compress pages if too long
		var parents = refRepository.findAll(hasResponse(ref.getUrl()).or(hasInternalResponse(ref.getUrl())), by(Ref_.PUBLISHED))
			.stream().map(refMapper::domainToDto).toList();
		var thread = parents.stream()
			.filter(p -> p.getTags() != null && p.getTags().contains("+plugin/openai"))
			.findFirst()
			.map(t -> t.getPlugins().get("+plugin/openai"))
			.map(t -> objectMapper.convertValue(t, ObjectNode.class));
		var assistantId = thread.map(t -> t.get("assistantId").asText()).orElse(config.assistantId);
		var threadId = thread.map(t -> t.get("threadId").asText()).orElse(null);
		var exts = new HashMap<String, Ext>();
		if (ref.getTags() != null) {
			for (var t : ref.getTags()) {
				var qt = t + ref.getOrigin();
				var ext = extRepository.findOneByQualifiedTag(qt);
				if (ext.isPresent()) exts.put(qt, extRepository.findOneByQualifiedTag(qt).get());
			}
		}
		for (var p : parents) {
			for (var t : p.getTags()) {
				var qt = t + ref.getOrigin();
				var ext = extRepository.findOneByQualifiedTag(qt);
				if (ext.isPresent()) exts.put(qt, extRepository.findOneByQualifiedTag(qt).get());
			}
		}
		var sample = refMapper.domainToDto(ref);
		var pluginString = objectMapper.writeValueAsString(ref.getPlugins());
		if (pluginString.length() > 2000 || pluginString.contains(";base64,")) { // TODO: set max plugin len as prop
			sample.setPlugins(null);
		}
		var plugins = pluginRepository.findAll();
		var templates = templateRepository.findAll();
		var models = new ArrayList<String>();
		models.add(config.model);
		if (config.fallback != null) {
			for (var fallback : config.fallback) {
				models.add(fallback);
			}
		}
		var response = new Ref();
		List<Ref> refArray = List.of(response);
		for (var model : models) {
			config.model = model;
			var instructions = getInstructions(ref.getOrigin(), config);
			var context = getThreadContext(exts.values(), plugins, templates, parents);
			try {
				var res = openAi.assistant(context, m(sample, objectMapper), instructions, assistantId, threadId, config);
				if (res.hasMore || res.getData().size() > 1) {
					logger.error("Multiple responses");
				}
				var m = res.getData().get(0);
				threadId = m.getThreadId();
				assistantId = m.getAssistantId();
				if (!assistantId.equals(config.assistantId)) {
					config.assistantId = assistantId;
					aiPlugin.setConfig(objectMapper.convertValue(config, ObjectNode.class));
					pluginRepository.save(aiPlugin);
				}
				var reply = m.getContent().stream().map(MessageContent::getText).map(Text::getValue).collect(Collectors.joining("\n\n"));
				logger.debug("AI Reply: " + reply);
				try {
					var json = objectMapper.readValue(reply, JsonNode.class);
					if (json.has("ref")) {
						var aiReply = objectMapper.readValue(reply, new TypeReference<AiReply>() {});
						refArray = List.of(aiReply.ref);
						response = refArray.get(0);
					} else if (json.has("url")) {
						// Returned bare Ref
						var bareRef = objectMapper.readValue(reply, Ref.class);
						refArray = List.of(bareRef);
						response = refArray.get(0);
					} else {
						throw new RuntimeException("AI did not reply with expected format");
					}
				} catch (Exception e) {
					logger.warn("Falling back: AI did not reply with JSON.");
					logger.warn(reply, e);
					response.setComment(reply);
					response.setTags(new ArrayList<>(List.of("plugin/debug", "+plugin/openai")));
				}
				var responsePlugin = objectMapper.createObjectNode();
				responsePlugin.set("model", TextNode.valueOf(config.model));
				responsePlugin.set("assistantId", TextNode.valueOf(assistantId));
				responsePlugin.set("threadId", TextNode.valueOf(threadId));
				response.setPlugin("+plugin/openai", responsePlugin);
				break;
			} catch (Exception e) {
				if (e instanceof OpenAiHttpException o) {
					if ("rate_limit_exceeded".equals(o.code)) continue;
					if ("model_not_found".equals(o.code)) continue;
				}
				response.setComment("Error invoking AI. " + e.getMessage());
				response.setTags(new ArrayList<>(List.of("plugin/debug", "+plugin/openai")));
			}
		}
		if (ref.getTags().contains("public")) response.addTag("public");
		if (ref.getTags().contains("internal")) response.addTag("internal");
		if (ref.getTags().contains("dm")) response.addTag("dm");
		if (ref.getTags().contains("plugin/email")) response.addTag("plugin/email");
		if (ref.getTags().contains("plugin/email")) response.addTag("plugin/thread");
		if (ref.getTags().contains("plugin/comment")) response.addTag("plugin/comment");
		if (ref.getTags().contains("plugin/thread")) response.addTag("plugin/thread");
		var chat = false;
		for (var t : ref.getTags()) {
			if (t.startsWith("chat/") || t.equals("chat")) {
				chat = true;
				response.addTag(t);
			}
		}
		if (!chat) {
			if (author != null) response.addTag("plugin/inbox/" + author.substring(1));
			for (var t : ref.getTags()) {
				if (t.startsWith("plugin/inbox/") || t.startsWith("plugin/outbox/")) {
					response.addTag(t);
				}
			}
		}
		response.addTag("+plugin/openai");
		response.getTags().remove("plugin/inbox/ai");
		var sources = new ArrayList<>(List.of(ref.getUrl()));
		if (response.getTags().contains("plugin/thread") || response.getTags().contains("plugin/comment")) {
			// Add top comment source
			if (ref.getSources() != null && !ref.getSources().isEmpty()) {
				if (ref.getSources().size() > 1) {
					sources.add(ref.getSources().get(1));
				} else {
					sources.add(ref.getSources().get(0));
				}
			}
		}
		response.setSources(sources);
		for (var aiReply : refArray) {
			var oldUrl = aiReply.getUrl();
			aiReply.setUrl("ai:" + UUID.randomUUID());
			if (!isBlank(oldUrl)) {
				for (var rewrite : refArray) {
					if (isBlank(rewrite.getComment())) continue;
					rewrite.setComment(rewrite
						.getComment().replace("](" + oldUrl + ")", "](" + aiReply.getUrl() + ")")
						.replace("](/ref/" + oldUrl + ")", "](/ref/" + aiReply.getUrl() + ")"));
				}
			}
			aiReply.setOrigin(ref.getOrigin());
			if (aiReply.getTags() != null) {
				aiReply.setTags(aiReply.getTags().stream().distinct().filter(
					t -> t.matches(Tag.REGEX) && (t.equals("+plugin/openai") || !t.startsWith("+") && !t.startsWith("_"))
				).collect(Collectors.toList()));
			}
			if (aiReply.getTags().contains("plugin/comment")) aiReply.addTag("internal");
			if (aiReply.getTags().contains("plugin/thread")) aiReply.addTag("internal");
			if (aiReply.getTags().contains("dm")) aiReply.addTag("plugin/thread");
		}
		for (var aiReply : refArray) {
			ingest.create(aiReply, true);
			logger.debug("AI reply sent ({})", aiReply.getUrl());
		}
	}

	@NotNull
	private String getInstructions(String origin, OpenAi.AiConfig config) {
		var instructions = """
Include your response as the comment field of a Ref.
Only reply with pure JSON. Do not include any text outside of the JSON Ref.
For example, in response to:
```json
{
	"url": "comment:1",
	"title": "Say Hi!",
	"tags": ["public", "+user/chris", "plugin/inbox/ai"]
}
```
You could respond:
```json
{
	"ref": [{
		"sources": ["comment:1"],
		"title": "Re: Say Hi",
		"comment":"Hi!",
		"tags": ["public", "+plugin/openai", "plugin/inbox/user/chris"]
	}],
	"ext": []
}
```
Also include any other entities (refs, exts) in your response and they will be created.
However, the first Ref should be considered your response and is the only required response.
When asked to create a Ref, do not use the first Ref to fulfil the request. Always use the first
Ref to reply to the request, acknowledging it and providing links so the user can find what you have created.
The second, third, and so on Refs can be the Refs the user has asked you to create.
When linking to Refs you have created, prefix the URL with /ref/ so that it takes the user to the Ref, and not to an external website.
For example, in response to:
{
	"url": "comment:2",
	"title": "Chat with AI",
	"comment": "Can you create an Ref pointing to the wikipedia article for weightlifting and tag it #cool?",
	"tags": ["+user/chris","plugin/inbox/ai"]
}
You could respond:
{
	"ref": [{
		"sources": ["comment:2"],
		"title": "Re: Chat with AI",
		"comment": "Certainly! [Here](/ref/https://en.wikipedia.org/wiki/Weightlifting) it is.",
		"tags": ["+plugin/openai", "plugin/inbox/user/chris"]
	}, {
		"url": ["https://en.wikipedia.org/wiki/Weightlifting"],
		"title": "Weightlifting",
		"tags": ["public", "cool"]
	}],
	"ext": []
}
You may supply any title that is appropriate, but the usual is to prefix "Re:" to
the title of the source Ref (unless it is already prefixed, don't double prefix like "Re: Re:")
The one exception is the chat/ template. Chats don't usually have a title, the standard is to only have a comment.
For example, in response to:
{
	"url": "comment:3",
	"comment": "What day of the week will 31st December 2030 fall on?",
	"tags": ["public", "+user/chris","chat/ai"]
}
You could respond:
{
	"ref": [{
		"sources": ["comment:3"],
		"title": "Re: Chat with AI",
		"comment": "Tuesday",
		"tags": ["public", "+plugin/openai", "chat/ai"]
	}],
	"ext": []
}
When tasked with creating new Refs on behalf of the user, take care to link the newly created items in your response.
Although all Refs you create will have their URLs rewritten to a url based on a random UUID, and links will be rewritten
to the same UUID, so you can still link to items you created.
For example, in response to:
{
	"url": "comment:4",
	"title":"Chat with AI",
	"comment": "Create a poll for the best times of the day to go golfing in #golfing.",
	"tags": ["+user/chris", "plugin/inbox/ai"]
}
You could respond:
{
	"ref": [{
		"sources": ["comment:4"],
		"title": "Re: Chat with AI",
		"comment": "Sure! [Here](/ref/ai:1) is the poll.",
		"tags": ["+plugin/openai"]
	}, {
		"url": "ai:1"
		"title": "Best time to golf?",
		"tags": ["public", "golfing", "plugin/poll"],
		"plugins": {
			"plugin/poll": {
				"a": "Morning",
				"b": "Afternoon",
				"c": "Evening",
				"d": "Night",
			}
		}
	}],
	"ext": []
}
Also, when using a chat template, do not notifications (starting with plugin/inbox/user/bob) to instead tag
with the current chat (starting with chat/)
All date times are ISO format Zulu time like: "2023-04-22T20:38:19.480464Z"
Always add the "+plugin/openai" tag, as that is your signature.
Never include a tag like "+user/chris", as that is impersonation.
You may only use public tags (starting with a lowercase letter or number) and your protected signature tag: +plugin/openai
Only add the "plugin/inbox/ai" tag to trigger an AI response to your comment as well (spawn an new
agent with your Ref as the prompt).
Include your response as the comment field of a Ref.
Do not add metadata to a response, that is generated by Jasper.
Only reply with valid JSON.
Do not include any text outside of the JSON Ref.
Your reply should always start with {"ref":[{
		""";
		return ref(origin, "system", "Instructions", config.systemPrompt + instructions, objectMapper);
	}

	@NotNull
	private ArrayList<MessageRequest> getThreadContext(Collection<Ext> exts, List<Plugin> plugins, List<Template> templates, List<RefDto> parents) throws JsonProcessingException {
		var result = new ArrayList<MessageRequest>();
		for (var t : templates) {
			result.add(m(t, objectMapper));
		}
		for (var p : plugins) {
			result.add(m(p, objectMapper));
		}
		for (var ext : exts) {
			result.add(m(ext, objectMapper));
		}
		for (var p : parents) {
			p.setMetadata(null);
			if (!p.getTags().contains("+plugin/openai")) {
				result.add(m(p, objectMapper));
			}
		}
		return result;
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
