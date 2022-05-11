package jasper.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static jasper.domain.Ref.URL_LEN;

@RestController
@RequestMapping("api/v1/cors")
@Validated
public class CorsBusterController {

	@Autowired
	RestTemplate restTemplate;

	@GetMapping("twitter")
	JsonNode getTweet(
		@RequestParam @Length(max = URL_LEN) @URL String url,
		@RequestParam(required = false) String theme,
		@RequestParam(required = false) Integer maxwidth,
		@RequestParam(required = false) Integer maxheight
	) {
		return restTemplate
			.getForEntity(new DefaultUriBuilderFactory("https://publish.twitter.com/oembed?url=" + url)
					.builder()
					.queryParam("theme", theme)
					.queryParam("maxwidth", maxwidth)
					.queryParam("maxheight", maxheight)
					.build(),
				JsonNode.class)
			.getBody();
	}
}
