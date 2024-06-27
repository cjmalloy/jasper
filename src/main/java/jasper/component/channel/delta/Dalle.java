package jasper.component.channel.delta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jasper.component.ConfigCache;
import jasper.component.Ingest;
import jasper.component.OpenAi;
import jasper.component.Storage;
import jasper.component.WebScraper;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.domain.proj.Tag;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
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
	RefRepository refRepository;

	@Autowired
	ConfigCache configs;

	@Autowired
	Optional<Storage> storage;

	@Autowired
	ObjectMapper objectMapper;

	@PostConstruct
	void init() {
		async.addAsyncTag("plugin/inbox/ai/dalle", this);
	}

	@Override
	public String signature() {
		return "+plugin/ai/dalle";
	}

	@Override
	public void run(Ref ref) throws JsonProcessingException {
		logger.debug("DALL-E replying to {} ({})", ref.getTitle(), ref.getUrl());
		var author = ref.getTags().stream().filter(User::isUser).findFirst().orElse(null);
		var config = configs.getPluginConfig("+plugin/ai/dalle", ref.getOrigin(), OpenAi.DalleConfig.class);
		Ref response;
		try {
			var useUrl = storage.isEmpty();
			var res = openAi.dale(
				ref.getOrigin(),
				getPrompt(ref.getTitle(), ref.getComment()),
				useUrl,
				config
			).getData().get(0);
			if (useUrl) {
				var asyncCache = webScraper.fetch(res.getUrl(), ref.getOrigin());
				response = refRepository.findOneByUrlAndOrigin(res.getUrl(), ref.getOrigin())
					.orElseThrow(() -> new NotFoundException("Async cache was not created: " + asyncCache.getId()));
			} else {
				var image = Base64.getDecoder().decode(res.getB64Json());
				try {
					var cache = webScraper.cache(ref.getOrigin(), image, "image/png", "+plugin/ai/dalle");
					response = refRepository.findOneByUrlAndOrigin("internal:" + cache.getId(), ref.getOrigin())
						.orElseThrow(() -> new NotFoundException("internal:" + cache.getId()));
				} catch (IOException e) {
					logger.error("Failed to cache DALL-E image", e);
					throw e;
				}
			}
			response.addTag("plugin/image");
			response.addTag("plugin/thumbnail");
		} catch (Exception e) {
			response = new Ref();
			response.setComment("Error invoking DALL-E. " + e.getMessage());
		}
		response.setTitle(getTitle(ref.getTitle(), ref.getComment()));
		response.addTags(ref.getTags().stream().filter(Tag::publicTag).toList());
		response.addTag("plugin/thread");
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
		response.addTag("+plugin/ai/dalle");
		response.getTags().remove("plugin/inbox/ai");
		response.getTags().remove("plugin/inbox/ai/dalle");
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
		if (isBlank(response.getUrl())) {
			response.setUrl("dalle:" + UUID.randomUUID());
			ingest.create(response, false);
		} else {
			ingest.update(response, false);
		}
		logger.debug("DALL-E reply sent ({})", response.getUrl());
	}

	private String getPrompt(String title, String comment) {
		if (isBlank(title)) return comment;
		if (isBlank(comment)) return title;
		return title + ": " + comment;
	}

	private String getTitle(String title, String comment) {
		if (isBlank(title)) {
			if (isBlank(comment)) return "Re: ";
			return "Re: " + comment.substring(0, Math.min(52, comment.length()));
		}
		if (title.startsWith("Re:")) return title;
		return "Re: " + title;
	}
}
