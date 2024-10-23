package jasper.web.rest;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jasper.domain.Ref;
import jasper.domain.proj.HasOrigin;
import jasper.service.ProxyService;
import jasper.service.dto.RefDto;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.concurrent.TimeUnit;

import static jasper.domain.Ref.URL_LEN;
import static jasper.domain.proj.HasOrigin.ORIGIN_LEN;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@RestController
@RequestMapping("api/v1/proxy")
@Validated
@Tag(name = "Proxy")
public class ProxyController {

	@Autowired
	ProxyService proxyService;

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping("prefetch")
	ResponseEntity<String> preFetch(
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		proxyService.preFetch(url, origin);
		return ResponseEntity.noContent()
			.cacheControl(CacheControl.maxAge(100, TimeUnit.DAYS).cachePrivate())
			.build();
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "404"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping()
	void fetch(
		WebRequest request,
		HttpServletResponse response,
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam(defaultValue = "false") boolean thumbnail
	) throws IOException {
		var cache = proxyService.fetch(url, origin);
		if (url.startsWith("cache:") || cache != null && isNotBlank(cache.getId())) {
			var id = url.startsWith("cache:") ? url.substring("cache:".length()) : cache.getId();
			response.setHeader(HttpHeaders.ETAG, "\"" + id.replaceAll("\\W", "") + "\"");
			response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(5, TimeUnit.DAYS).mustRevalidate().cachePrivate().getHeaderValue());
			if (cache != null && isNotBlank(cache.getMimeType())) response.setHeader(HttpHeaders.CONTENT_TYPE, cache.getMimeType());
			if (request.checkNotModified(id)) {
				if (cache != null && cache.getContentLength() != null) response.setIntHeader(HttpHeaders.CONTENT_LENGTH, Math.toIntExact(cache.getContentLength()));
				response.sendError(HttpStatus.NOT_MODIFIED.value());
			} else {
				response.setStatus(HttpStatus.OK.value());
				try (var os = response.getOutputStream()) {
					proxyService.fetch(url, origin, thumbnail, os);
				}
			}
		} else {
			response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(0, TimeUnit.MILLISECONDS).mustRevalidate().cachePrivate().getHeaderValue());
			response.setStatus(HttpStatus.NOT_FOUND.value());
		}
	}

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping()
	RefDto save(
		@RequestParam(required = false) String mime,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		InputStream data
	) throws IOException {
		return proxyService.save(origin, data, mime);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@DeleteMapping()
	void clearDeleted(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		proxyService.clearDeleted(origin);
	}
}
