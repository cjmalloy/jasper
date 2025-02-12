package jasper.web.rest;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jasper.domain.Ref;
import jasper.domain.proj.HasOrigin;
import jasper.errors.NotFoundException;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static jasper.domain.Ref.URL_LEN;
import static jasper.domain.proj.HasOrigin.ORIGIN_LEN;
import static org.apache.commons.io.FilenameUtils.getName;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.http.MediaType.parseMediaType;

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
	@GetMapping
	ResponseEntity<StreamingResponseBody> fetch(
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam(defaultValue = "false") boolean thumbnail
	) {
		var is = proxyService.fetch(url, origin, thumbnail);
		if (is == null) throw new NotFoundException(url);
		var ref = proxyService.stat(url, origin, thumbnail);
		String filename = "file";
		try {
			filename
				= isNotBlank(getName(new URI(url).getPath())) ? getName(new URI(url).getPath())
				: ref != null && isNotBlank(ref.getTitle()) ? ref.getTitle()
				: filename;
		} catch (URISyntaxException ignored) { }
		var response = ResponseEntity.ok();
		var cache = proxyService.cache(url, origin, thumbnail);
		if (cache != null && cache.getContentLength() != null) response.contentLength(cache.getContentLength());
		return response
			.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"))
			.contentType(cache != null && isNotBlank(cache.getMimeType())? parseMediaType(cache.getMimeType()) : APPLICATION_OCTET_STREAM)
			.cacheControl(CacheControl.maxAge(100, TimeUnit.DAYS).cachePrivate())
			.body(outputStream -> {
				try (is) {
					byte[] buffer = new byte[64 * 1024];
					int bytesRead;
					while ((bytesRead = is.read(buffer)) != -1) {
						outputStream.write(buffer, 0, bytesRead);
					}
				}
			});
	}

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping
	RefDto save(
		@RequestParam(required = false) String title,
		@RequestParam(required = false) String mime,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		InputStream data
	) throws IOException {
		return proxyService.save(origin, title, data, mime);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "500", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@DeleteMapping
	void clearDeleted(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		proxyService.clearDeleted(origin);
	}
}
