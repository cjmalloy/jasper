package jasper.web.rest;

import com.github.fge.jsonpatch.JsonPatch;
import com.rometools.rome.io.FeedException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jasper.domain.Feed;
import jasper.domain.Origin;
import jasper.domain.Ref;
import jasper.repository.filter.RefFilter;
import jasper.service.FeedService;
import jasper.service.dto.FeedDto;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import java.io.IOException;
import java.time.Instant;

import static jasper.domain.Origin.ORIGIN_LEN;
import static jasper.domain.Ref.URL_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static jasper.util.RestUtil.ifModifiedSince;

@RestController
@RequestMapping("api/v1/feed")
@Validated
@Tag(name = "Feed")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
})
public class FeedController {

	@Autowired
	FeedService feedService;

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
		@ApiResponse(responseCode = "404", description = "Foreign Write", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createFeed(
		@RequestBody @Valid Feed feed
	) {
		feedService.create(feed);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping
	HttpEntity<FeedDto> getFeed(
		WebRequest request,
		@RequestParam @Length(max = URL_LEN) @URL String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin
	) {
		return ifModifiedSince(request, feedService.get(url, origin));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("page")
	Page<FeedDto> getFeedPage(
		@PageableDefault(sort = "modified", direction = Direction.DESC) @ParameterObject Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return feedService.page(
			RefFilter.builder()
				.query(query)
				.url(url)
				.modifiedAfter(modifiedAfter).build(),
			pageable);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateFeed(
		@RequestBody @Valid Feed feed
	) {
		feedService.update(feed);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PatchMapping(consumes = "application/json-patch+json")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void patchFeed(
		@RequestParam @Length(max = URL_LEN) @URL String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin,
		@RequestBody JsonPatch patch
	) {
		feedService.patch(url, origin, patch);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteFeed(
		@RequestParam @Length(max = URL_LEN) @URL String url
	) {
		feedService.delete(url);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "503", description = "Error scraping", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping("scrape")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void scrape(
		@RequestParam @Length(max = URL_LEN) @URL String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin
	) throws FeedException, IOException {
		feedService.scrape(url, origin);
	}
}
