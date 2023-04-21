package jasper.component.delta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.CompletionChoice;
import jasper.component.Ingest;
import jasper.component.OpenAi;
import jasper.component.scheduler.Async;
import jasper.domain.Ref;
import jasper.domain.User;
import jasper.errors.NotFoundException;
import jasper.repository.PluginRepository;
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

@Profile("summary")
@Component
public class Summary implements Async.AsyncRunner {
	private static final Logger logger = LoggerFactory.getLogger(Summary.class);

	@Autowired
	Async async;

	@Autowired
	Ingest ingest;

	@Autowired
	OpenAi openAi;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	ObjectMapper objectMapper;

	@PostConstruct
	void init() {
		async.addAsync("plugin/summary", this);
	}

	@Override
	public void run(Ref ref) {
		if (ref.hasPluginResponse("+plugin/summary")) return;
		var author = ref.getTags().stream().filter(User::isUser).findFirst().orElse(null);
		var summaryPlugin = pluginRepository.findByTagAndOrigin("+plugin/summary", ref.getOrigin())
			.orElseThrow(() -> new NotFoundException("+plugin/summary"));
		var config = objectMapper.convertValue(summaryPlugin.getConfig(), SummaryConfig.class);
		var response = new Ref();
		try {
			var res = openAi.completion("", String.join("\n\n",
				config.getSystemPrompt(),
				"Title: " + ref.getTitle(),
				"Tags: " + String.join(", ", ref.getTags()),
				ref.getComment()));
			response.setComment(res.getChoices().stream().map(CompletionChoice::getText).collect(Collectors.joining("\n\n")));
			response.setUrl("ai:" + res.getId());
		} catch (Exception e) {
			response.setComment("Error creating the summary. " + e.getMessage());
			response.setUrl("internal:" + UUID.randomUUID());
		}
		var title = ref.getTitle();
		if (!title.startsWith(config.getTitlePrefix())) title = config.titlePrefix + title;
		response.setTitle(title);
		response.setOrigin(ref.getOrigin());
		response.setTags(new ArrayList<>(List.of("+plugin/summary")));
		var tags = new ArrayList<String>();
		if (ref.getTags().contains("public")) tags.add("public");
		if (ref.getTags().contains("dm")) tags.add("dm");
		if (ref.getTags().contains("dm")) tags.add("internal");
		if (ref.getTags().contains("internal")) tags.add("internal");
		if (ref.getTags().contains("plugin/comment")) tags.add("internal");
		if (ref.getTags().contains("plugin/comment")) tags.add("plugin/comment");
		if (ref.getTags().contains("plugin/comment")) tags.add("plugin/thread");
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
		ingest.ingest(response, false);
	}

	@Getter
	@Setter
	private static class SummaryConfig {
		private String titlePrefix;
		private String systemPrompt;
	}
}
