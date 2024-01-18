package jasper.web.rest;

import com.rometools.rome.io.FeedException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jasper.domain.proj.HasOrigin;
import jasper.service.ScrapeService;
import jasper.service.dto.RefDto;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Pattern;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static jasper.domain.Ref.URL_LEN;
import static jasper.domain.proj.HasOrigin.ORIGIN_LEN;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@RestController
@RequestMapping("api/v1/scrape")
@Validated
@Tag(name = "Scrape")
public class ScrapeController {

	@Autowired
	ScrapeService scrapeService;

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "503", description = "Error scraping", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping("feed")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void scrapeFeed(
		@RequestParam @Length(max = URL_LEN) @URL String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) throws FeedException, IOException {
		scrapeService.feed(url, origin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping("web")
	ResponseEntity<RefDto> scrapeWebpage(@RequestParam @Length(max = URL_LEN) @URL String url) throws IOException, URISyntaxException {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(100, TimeUnit.DAYS).cachePublic())
			.body(scrapeService.webpage(url));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping("fetch")
	ResponseEntity<byte[]> fetch(@RequestParam @Length(max = URL_LEN) String url) {
		var cache = scrapeService.fetch(url);
		if (cache != null && cache.getData() != null) return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(100, TimeUnit.DAYS).cachePublic())
			.contentType(isNotBlank(cache.getMime()) ? MediaType.valueOf(cache.getMime()) : null)
			.body(cache.getData());
		return ResponseEntity.noContent()
			.cacheControl(CacheControl.noCache())
			.build();
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping("rss")
	ResponseEntity<String> rss(@RequestParam @Length(max = URL_LEN) String url) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(100, TimeUnit.DAYS).cachePrivate())
			.body(scrapeService.rss(url));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping
	ResponseEntity<Void> scrape(@RequestParam @Length(max = URL_LEN) String url) throws URISyntaxException, IOException {
		scrapeService.scrape(url);
		return ResponseEntity.noContent()
			.cacheControl(CacheControl.maxAge(100, TimeUnit.DAYS).cachePrivate())
			.build();
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping("cache")
	String cache(
		@RequestParam(required = false) String mime,
		@RequestBody byte[] data
	) {
		return scrapeService.cache(data, mime);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@PostMapping("clear-config-cache")
	void clearConfigCache() {
		scrapeService.clearCache();
	}
}
