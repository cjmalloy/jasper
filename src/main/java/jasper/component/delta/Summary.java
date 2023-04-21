package jasper.component.delta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.CompletionChoice;
import jasper.component.Ingest;
import jasper.component.OpenAi;
import jasper.component.scheduler.Async;
import jasper.domain.Ref;
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
	ObjectMapper objectMapper;

	@PostConstruct
	void init() {
		async.addAsync("plugin/summary", this);
	}

	@Override
	public void run(Ref ref) {
		if (ref.hasPluginResponse("+plugin/summary")) return;
		var response = new Ref();
		try {
			var res = openAi.completion("", String.join("\n\n",
				"Summarize the following:",
				"Title: " + ref.getTitle(),
				"Tags: " + String.join(", ", ref.getTags()),
				ref.getComment()));
			response.setComment(res.getChoices().stream().map(CompletionChoice::getText).collect(Collectors.joining("\n\n")));
			response.setUrl("ai:" + res.getId());
		} catch (Exception e) {
			response.setComment("Error creating the summary. " + e.getMessage());
			response.setUrl("internal:" + UUID.randomUUID());
		}
		response.setOrigin(ref.getOrigin());
		response.setSources(List.of(ref.getUrl()));
		response.setTags(new ArrayList<>(List.of("public", "summary", "+plugin/summary", "internal", "plugin/comment")));
		ingest.ingest(response, false);
	}
}
