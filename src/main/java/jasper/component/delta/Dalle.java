package jasper.component.delta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.Ingest;
import jasper.component.OpenAi;
import jasper.component.WebScraper;
import jasper.component.scheduler.Async;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.domain.proj.Tag;
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
			response.setTitle(isBlank(ref.getTitle()) ? "" : (ref.getTitle().startsWith("Re:") ? "" : "Re: ") + ref.getTitle());
			response.setUrl(res.getData().get(0).getUrl());
		} catch (Exception e) {
			response.setComment("Error invoking DALL-E. " + e.getMessage());
			response.setUrl("internal:" + UUID.randomUUID());
		}
		response.addTags(ref.getTags().stream().filter(Tag::publicTag).toList());
		response.addTag("plugin/thread");
		response.addTag("plugin/image");
		response.addTag("plugin/thumbnail");
		var chat = ref.getTags().stream().filter(t -> t.startsWith("chat/") || t.equals("chat")).findFirst();
		if (chat.isPresent()) {
			response.addTag(chat.get());
		} else {
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
		response.setOrigin(ref.getOrigin());
		ingest.create(response, false);
		logger.debug("DALL-E reply sent ({})", response.getUrl());
		webScraper.scrapeAsync(response.getUrl());
	}
}
