package jasper.component.delta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatMessage;
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
import org.apache.commons.lang3.StringUtils;
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

import static jasper.component.OpenAi.cm;
import static jasper.repository.spec.RefSpec.hasInternalResponse;
import static jasper.repository.spec.RefSpec.hasResponse;
import static jasper.repository.spec.RefSpec.isNotObsolete;
import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.concat;
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
		var context = new HashMap<String, RefDto>();
		var parents = refRepository.findAll(isNotObsolete().and(hasResponse(ref.getUrl()).or(hasInternalResponse(ref.getUrl()))), by(Ref_.PUBLISHED))
			.stream().map(refMapper::domainToDto).toList();
		parents.forEach(p -> context.put(p.getUrl(), p));
		for (var i = 0; i < config.maxContext; i++) {
			if (parents.isEmpty()) break;
			var grandParents = parents.stream().flatMap(p -> refRepository.findAll(isNotObsolete().and(hasResponse(p.getUrl()).or(hasInternalResponse(p.getUrl()))), by(Ref_.PUBLISHED)).stream())
				.map(refMapper::domainToDto).toList();
			var newParents = new ArrayList<RefDto>();
			for (var p : grandParents) {
				if (!context.containsKey(p.getUrl())) {
					newParents.add(p);
					context.put(p.getUrl(), p);
				}
			}
			parents = newParents;
		}
		var exts = new HashMap<String, Ext>();
		if (ref.getTags() != null) {
			for (var t : ref.getTags()) {
				var qt = t + ref.getOrigin();
				var ext = extRepository.findOneByQualifiedTag(qt);
				if (ext.isPresent()) exts.put(qt, extRepository.findOneByQualifiedTag(qt).get());
			}
		}
		for (var p : context.values()) {
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
			models.addAll(config.fallback);
		}
		var response = new Ref();
		List<Ref> refArray = List.of(response);
		for (var model : models) {
			config.model = model;
			var messages = getChatMessages(ref, exts.values(), plugins, templates, config, context.values(), author, sample);
			try {
				var res = openAi.chat(messages, config);
				var reply = res.getChoices().stream().map(ChatCompletionChoice::getMessage).map(ChatMessage::getContent).collect(Collectors.joining("\n\n"));
				logger.debug("AI Reply: " + reply);
				if (reply.startsWith("```json")) {
					reply = reply.substring("```json".length(), reply.length() - "```".length());
				}
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
				var responsePlugin = objectMapper.convertValue(res.getUsage(), ObjectNode.class);
				responsePlugin.set("model", TextNode.valueOf(config.model));
				response.setPlugin("+plugin/openai", responsePlugin);
				break;
			} catch (Exception e) {
				if (e instanceof OpenAiHttpException o) {
					if (o.code.equals("rate_limit_exceeded")) continue;
					if (o.code.equals("model_not_found")) continue;
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
		var chat = ref.getTags().stream().anyMatch(t -> t.startsWith("chat/") || t.equals("chat"));
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
		if (ref.getTags().contains("plugin/thread") || ref.getTags().contains("plugin/comment")) {
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
			if (isBlank(oldUrl) || !oldUrl.startsWith("http:") && !oldUrl.startsWith("https:")) {
				aiReply.setUrl("ai:" + UUID.randomUUID());
				if (!isBlank(oldUrl)) {
					for (var rewrite : refArray) {
						if (isBlank(rewrite.getComment())) continue;
						rewrite.setComment(rewrite
							.getComment().replace("](" + oldUrl + ")", "](" + aiReply.getUrl() + ")")
							.replace("](/ref/" + oldUrl + ")", "](/ref/" + aiReply.getUrl() + ")"));
					}
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
	private ArrayList<ChatMessage> getChatMessages(Ref ref, Collection<Ext> exts, Collection<Plugin> plugins, Collection<Template> templates, OpenAi.AiConfig config, Collection<RefDto> context, String author, RefDto sample) throws JsonProcessingException {
		var modsPrompt = concat(
			plugins.stream().map(Plugin::getConfig),
			templates.stream().map(Template::getConfig)
		)
			.flatMap(nc -> ofNullable(nc)
				.map(c -> c.get("aiInstructions"))
				.map(JsonNode::asText)
				.stream())
			.filter(StringUtils::isNotBlank)
			.reduce("", (a, b) -> a + "\n\n" + b);
		var instructions = """
Include your response as the comment field of a Ref.
Only reply with pure JSON. Do not include any text outside of the JSON Ref.
For example, in response to:
{
	"url": "comment:1",
	"title": "Say Hi!",
	"tags": ["public", "+user/chris", "plugin/inbox/ai"]
}

You could respond:
{
	"ref": [{
		"sources": ["comment:1"],
		"title": "Re: Say Hi",
		"comment":"Hi!",
		"tags": ["public", "plugin/inbox/user/chris", "+plugin/openai"]
	}],
	"ext": []
}
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
		"tags": ["plugin/inbox/user/chris", "+plugin/openai"]
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
If you create a ref with an http or https url, it will not be rewritten. If you want the url rewritten, use a url like ai:<uuid>.
All markdown links matching rewritten urls will also be updated.
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
		"tags": ["plugin/inbox/user/chris", "+plugin/openai"]
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
However, in response to requests to create web links, use http or https urls to prevent rewriting.
For example, in response to:
{
	"url": "comment:5",
	"title":"Chat with AI",
	"comment": "Create a link to the Wikipedia entry for Sparkline and tag it #data.",
	"tags": ["+user/chris", "plugin/inbox/ai"]
}
You could respond:
{
	"ref": [{
		"sources": ["comment:4"],
		"title": "Re: Chat with AI",
		"comment": "Sure! [Here](/ref/https://en.wikipedia.org/wiki/Sparkline) it is.",
		"tags": ["plugin/inbox/user/chris", "+plugin/openai"]
	}, {
		"url": "https://en.wikipedia.org/wiki/Sparkline"
		"title": "Sparkline - Wikipedia",
		"tags": ["public", "data"]
	}],
	"ext": []
}
Also, when using a chat template, do not notifications (starting with plugin/inbox/user/bob) to instead tag
with the current chat (starting with chat/)
""" + modsPrompt + """
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
		var extsPrompt = """
   The referenced Exts are:
	""" + objectMapper.writeValueAsString(exts);
		var messages = new ArrayList<>(List.of(
			cm(ref.getOrigin(), "system", "System Prompt", config.systemPrompt, objectMapper)
		));
		if (exts.isEmpty()) {
			messages.add(cm(ref.getOrigin(), "system", "Exts", extsPrompt, objectMapper));
		}
		for (var p : context) {
			p.setMetadata(null);
			if (p.getTags().contains("+plugin/openai")) {
				messages.add(cm("assistant", objectMapper.writeValueAsString(p)));
			} else {
				messages.add(cm("user", objectMapper.writeValueAsString(p)));
			}
		}
		if (author == null) {
			messages.add(cm(ref.getOrigin(), "system", "Spawning Agent Prompt",
				"You are following up on a previous ref to further a line of inquiry, " +
					"suggest the next course of action, or resolve the matter in your reply.", objectMapper));
		} else {
			messages.add(cm(ref.getOrigin(), "system", "Explanation of signature tag",
				"When you see the tag " + author + " it is the signature for the author of the Ref you must respond to.", objectMapper));
		}
		messages.add(cm("user", objectMapper.writeValueAsString(sample)));
		messages.add(cm(ref.getOrigin(), "system", "Output format instructions", instructions, objectMapper));
		return messages;
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
