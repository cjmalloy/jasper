package jasper.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jasper.service.OembedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URISyntaxException;
import java.util.Map;

@RestController
@RequestMapping("api/v1/oembed")
@Validated
@Tag(name = "oEmbed")
@ApiResponses({
	@ApiResponse(responseCode = "200"),
	@ApiResponse(responseCode = "400", content = @Content()),
	@ApiResponse(responseCode = "404", content = @Content()),
})
public class OEmbedController {

	@Autowired
	OembedService oembedService;

	@GetMapping()
	JsonNode oembed(@RequestParam Map<String, String> params) throws URISyntaxException, JsonProcessingException {
		return oembedService.get(params);
	}
}
