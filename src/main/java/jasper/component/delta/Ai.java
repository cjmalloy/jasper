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
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static jasper.component.OpenAi.cm;
import static jasper.repository.spec.RefSpec.hasInternalResponse;
import static jasper.repository.spec.RefSpec.hasResponse;

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
		var parents = refRepository.findAll(hasResponse(ref.getUrl()).or(hasInternalResponse(ref.getUrl())), Sort.by("published"))
			.stream().map(refMapper::domainToDto).toList();
		var sample = refMapper.domainToDto(ref);
		var pluginString = objectMapper.writeValueAsString(ref.getPlugins());
		if (pluginString.length() > 2000 || pluginString.contains(";base64,")) {
			sample.setPlugins(null);
		}
		var instructions = """
Include your response as the comment field of a Ref.
Only reply with pure JSON. Do not include any text outside of the JSON Ref.
For example, in response to:
```json
{"url":"comment:1","title":"Say Hi!","tags":["public","+user/chris","plugin/inbox/ai"]}
```
You could respond:
```json
{"ref":[{"sources":["comment:1"],"title":"Re: Say Hi","comment":"Hi!","tags":["public","ai","+plugin/openai","plugin/inbox/user/chris"]}],"ext":[]}
```
Also include any other entities (refs, exts) in your response and they will be created.
However, the first Ref should be considered your response and is the only required response.
When asked to create a Ref, do not use the first Ref to fulfil the request. Always use the first
Ref to reply to the request, acknowledging it and providing links so the user can find what you have created.
The second, third, and so on Refs can be the Refs the user has asked you to create.
When linking to Refs you have created, prefix the URL with /ref/ so that it takes the user to the Ref, and not to an external website.
For example, in response to:
```json
{"url":"comment:2","title":"Chat with AI","comment":"Can you create an Ref pointing to the wikipedia article for weightlifting and tag it #cool?","tags":["public","+user/chris","plugin/inbox/ai"]}
```
You could respond:
```json
{"ref":[{"sources":["comment:2"],"title":"Re: Chat with AI","comment":"Certainly! [Here](/ref/https://en.wikipedia.org/wiki/Weightlifting) it is.","tags":["public","ai","+plugin/openai","plugin/inbox/user/chris"]},{"url":["https://en.wikipedia.org/wiki/Weightlifting"],"title":"Weightlifting","tags":["public","cool"]}],"ext":[]}
```
You may supply any title that is appropriate, but the usual is to prefix "Re:" to
the title of the source Ref (unless it is already prefixed, don't double prefix like "Re: Re:")
The one exception is the chat/ template. Chats don't usually have a title, the standard is to only have a comment.
For example, in response to:
```json
{"url": "comment:3","comment": "What day of the week will 31st December 2030 fall on?","tags": ["public","+user/chris","chat/ai"]}
```
You could respond:
```json
{"ref":[{"sources":["comment:3"],"title":"Re: Chat with AI","comment":"Tuesday","tags":["public","ai","+plugin/openai","chat/ai"]}],"ext":[]}
```
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
		var response = new Ref();
		try {
			var messages = new ArrayList<>(List.of(
				cm(ref.getOrigin(), "system", "System Prompt", config.systemPrompt, objectMapper)
			));
			if (author == null) {
				messages.add(cm(ref.getOrigin(), "system", "Spawning Agent Prompt",
					"You are following up on a previous ref to further a line of inquiry, " +
					"suggest the next course of action, or resolve the matter in your reply.", objectMapper));
			}
			for (var p : parents) {
				p.setMetadata(null);
				if (p.getTags().contains("+plugin/openai")) {
					p.getPlugins().remove("+plugin/openai");
					messages.add(cm("assistant", objectMapper.writeValueAsString(p)));
				} else {
					messages.add(cm("user", objectMapper.writeValueAsString(p)));
				}
			}
			if (author != null) {
				messages.add(cm(ref.getOrigin(), "system", "Explanation of signature tag",
					"When you see the tag " + author + " it is the signature for the author of the Ref. ", objectMapper));
			}
			messages.add(cm("user", objectMapper.writeValueAsString(sample)));
			messages.add(cm(ref.getOrigin(), "system", "Output format instructions", instructions, objectMapper));
			var res = openAi.chat(config.model, messages);
			var reply = res.getChoices().stream().map(ChatCompletionChoice::getMessage).map(ChatMessage::getContent).collect(Collectors.joining("\n\n"));
			response.setUrl("ai:" + res.getId());
			response.setPlugin("+plugin/openai", objectMapper.convertValue(res.getUsage(), JsonNode.class));
			logger.trace("Reply: " + reply);
			response.setComment(reply);
		} catch (Exception e) {
			response.setComment("Error invoking AI. " + e.getMessage());
			response.setUrl("internal:" + UUID.randomUUID());
		}
		List<Ref> refArray = List.of(response);
		Exception ex = null;
		try {
			var json = objectMapper.readValue(response.getComment(), JsonNode.class);
			if (json.has("ref")) {
				var aiReply = objectMapper.readValue(response.getComment(), new TypeReference<AiReply>() {});
				refArray = List.of(aiReply.ref);
			} else if (json.has("url")) {
				// Returned bare Ref
				var bareRef = objectMapper.readValue(response.getComment(), Ref.class);
				refArray = List.of(bareRef);
			} else {
				logger.warn("Falling back: AI did not reply with expected format.");
				logger.warn(response.getComment());
				response.setTags(new ArrayList<>(List.of("plugin/debug", "ai", "+plugin/openai")));
			}
			refArray.get(0).setUrl(response.getUrl());
			refArray.get(0).setPlugin("+plugin/openai", response.getPlugins().get("+plugin/openai"));
		} catch (Exception e) {
			logger.warn("Falling back: AI did not reply with JSON.");
			logger.warn(response.getComment(), ex);
			response.setTags(new ArrayList<>(List.of("plugin/debug", "ai", "+plugin/openai")));
		}
		response = refArray.get(0);
		if (ref.getTags().contains("public")) response.addTag("public");
		if (ref.getTags().contains("internal")) response.addTag("internal");
		if (ref.getTags().contains("dm")) response.addTag("dm");
		if (ref.getTags().contains("dm")) response.addTag("plugin/thread");
		if (ref.getTags().contains("plugin/email")) response.addTag("plugin/email");
		if (ref.getTags().contains("plugin/email")) response.addTag("plugin/thread");
		if (ref.getTags().contains("plugin/comment")) response.addTag("plugin/comment");
		if (ref.getTags().contains("plugin/comment")) response.addTag("plugin/thread");
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
				aiReply.setTags(aiReply.getTags().stream().distinct().filter(
					t -> t.matches(Tag.REGEX) && (t.equals("+plugin/openai") || !t.startsWith("+") && !t.startsWith("_"))
				).collect(Collectors.toList()));
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
