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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.Pattern;
import java.io.IOException;
import java.io.InputStream;
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
			.cacheControl(CacheControl.maxAge(100, TimeUnit.DAYS).cachePrivate())
			.body(scrapeService.webpage(url));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping("fetch")
	void fetch(
		WebRequest request,
		HttpServletResponse response,
		@RequestParam @Length(max = URL_LEN) String url,
		@RequestParam(defaultValue = "false") boolean thumbnail
	) throws IOException {
		response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(5, TimeUnit.MINUTES).mustRevalidate().cachePrivate().getHeaderValue());
		var cache = scrapeService.fetch(url);
		if (cache != null) {
			response.setHeader(HttpHeaders.ETAG, "\"" + cache.getId() + "\"");
			if (isNotBlank(cache.getMimeType())) response.setHeader(HttpHeaders.CONTENT_TYPE, cache.getMimeType());
			if (request.checkNotModified(cache.getId())) {
				if (cache.getContentLength() != null) response.setIntHeader(HttpHeaders.CONTENT_LENGTH, Math.toIntExact(cache.getContentLength()));
				response.sendError(HttpStatus.NOT_MODIFIED.value());
			} else {
				response.setStatus(HttpStatus.OK.value());
				scrapeService.fetch(url, thumbnail, response.getOutputStream());
			}
		} else {
			response.setStatus(HttpStatus.OK.value());
		}
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping("rss")
	ResponseEntity<String> rss(@RequestParam @Length(max = URL_LEN) String url) throws IOException {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(100, TimeUnit.DAYS).cachePrivate())
			.body(scrapeService.rss(url));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping
	ResponseEntity<String> scrape(@RequestParam @Length(max = URL_LEN) String url) throws URISyntaxException, IOException {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(100, TimeUnit.DAYS).cachePrivate())
			.body(scrapeService.scrape(url));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping("refresh")
	String refresh(@RequestParam @Length(max = URL_LEN) String url) throws IOException {
		return scrapeService.refresh(url);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping("cache")
	String cache(
		@RequestParam(required = false) String mime,
		InputStream data
	) throws IOException {
		return scrapeService.cache(data, mime);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PostMapping("clear-deleted")
	void clearDeleted() {
		scrapeService.clearDeleted();
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PostMapping("clear-config-cache")
	void clearConfigCache() {
		scrapeService.clearCache();
	}
}
