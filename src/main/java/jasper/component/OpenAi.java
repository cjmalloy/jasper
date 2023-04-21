package jasper.component;

import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.CompletionResult;
import com.theokanning.openai.service.OpenAiService;
import jasper.config.Props;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static jasper.repository.spec.QualifiedTag.selector;

@Component
public class OpenAi {
	private static final Logger logger = LoggerFactory.getLogger(OpenAi.class);

	@Autowired
	Props props;

	@Autowired
	RefRepository refRepository;

	public CompletionResult completion(String prompt) {
		var key = refRepository.findAll(selector("_openai/key" + props.getLocalOrigin()).refSpec());
		if (key.isEmpty()) {
			throw new NotFoundException("requires openai api key");
		}
		OpenAiService service = new OpenAiService(key.get(0).getComment());
		CompletionRequest completionRequest = CompletionRequest.builder()
			.maxTokens(2048)
			.prompt(prompt)
			.model("text-davinci-003")
			.build();
		try {
			return service.createCompletion(completionRequest);
		} catch (OpenAiHttpException e) {
			if (e.statusCode == 400) {
				completionRequest.setMaxTokens(400);
				try {
					return service.createCompletion(completionRequest);
				} catch (OpenAiHttpException second) {
					throw e;
				}
			}
		}
		return null;
	}
}
