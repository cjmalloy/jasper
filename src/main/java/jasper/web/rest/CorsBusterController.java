package jasper.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import jasper.client.BitChuteClient;
import jasper.client.TwitterClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("api/v1/cors")
@Validated
@PreAuthorize("hasRole('USER')")
public class CorsBusterController {

	@Autowired
	TwitterClient twitterClient;

	@Autowired
	BitChuteClient bitChuteClient;

	@GetMapping("twitter")
	JsonNode getTweet(@RequestParam Map<String, String> params) {
		return twitterClient.oembed(params);
	}

	@GetMapping("bitChute")
	JsonNode getBitChute(@RequestParam Map<String, String> params) {
		return bitChuteClient.oembed(params);
	}
}
