package jasper.component.delta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.Ingest;
import jasper.component.OpenAi;
import jasper.component.WebScraper;
import jasper.component.scheduler.Async;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Profile("ai")
@Component
public class Dalle implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(Dalle.class);

	@Autowired
	Async async;

	@Autowired
	Ingest ingest;

	@Autowired
	OpenAi openAi;

	@Autowired
	WebScraper webScraper;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	ObjectMapper objectMapper;

	@PostConstruct
	void init() {
		async.addAsyncResponse("plugin/inbox/dalle", this);
	}

	@Override
	public String signature() {
		return "+plugin/dalle";
	}

	@Override
	public void run(Ref ref) throws JsonProcessingException {
		logger.debug("AI replying to {} ({})", ref.getTitle(), ref.getUrl());
		var author = ref.getTags().stream().filter(User::isUser).findFirst().orElse(null);
		var dallePlugin = pluginRepository.findByTagAndOrigin("+plugin/dalle", ref.getOrigin())
			.orElseThrow(() -> new NotFoundException("+plugin/dalle"));
		var config = objectMapper.convertValue(dallePlugin.getConfig(), OpenAi.DalleConfig.class);
		var response = new Ref();
		try {
			var res = openAi.dale((isBlank(ref.getTitle()) ? "" : ref.getTitle() + ": ") + ref.getComment(), config);
			response.setTitle("Re: " + ref.getTitle());
			response.setUrl(res.getData().get(0).getUrl());
		} catch (Exception e) {
			response.setComment("Error invoking DALL-E. " + e.getMessage());
			response.setUrl("internal:" + UUID.randomUUID());
		}
		if (ref.getTags().contains("public")) response.addTag("public");
		if (ref.getTags().contains("internal")) response.addTag("internal");
		if (ref.getTags().contains("dm")) response.addTag("dm");
		if (ref.getTags().contains("dm")) response.addTag("plugin/thread");
		if (ref.getTags().contains("plugin/email")) response.addTag("plugin/email");
		if (ref.getTags().contains("plugin/email")) response.addTag("plugin/thread");
		if (ref.getTags().contains("plugin/comment")) response.addTag("plugin/comment");
		if (ref.getTags().contains("plugin/comment")) response.addTag("plugin/thread");
		if (ref.getTags().contains("plugin/thread")) response.addTag("plugin/thread");
		response.addTag("plugin/image");
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
		response.addTag("+plugin/dalle");
		response.getTags().remove("plugin/inbox/dalle");
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
		response.setOrigin(ref.getOrigin());
		ingest.ingest(response, false);
		logger.debug("DALL-E reply sent ({})", response.getUrl());
		webScraper.scrapeAsync(response.getUrl());
	}
}
