package jasper.web.rest;

import com.github.fge.jsonpatch.JsonPatch;
import com.rometools.rome.io.FeedException;
import jasper.domain.Feed;
import jasper.domain.Origin;
import jasper.repository.filter.RefFilter;
import jasper.service.FeedService;
import jasper.service.dto.FeedDto;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import static jasper.util.RestUtil.ifModifiedSincePage;
import static jasper.util.RestUtil.sortedByTime;

@RestController
@RequestMapping("api/v1/feed")
@Validated
public class FeedController {

	@Autowired
	FeedService feedService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createFeed(
		@RequestBody @Valid Feed feed
	) {
		feedService.create(feed);
	}

	@GetMapping
	HttpEntity<FeedDto> getFeed(
		WebRequest request,
		@RequestParam @Length(max = URL_LEN) @URL String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin
	) {
		return ifModifiedSince(request, feedService.get(url, origin));
	}

	@GetMapping("exists")
	boolean feedExists(
		WebRequest request,
		@RequestParam @Length(max = URL_LEN) @URL String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin
	) {
		return feedService.exists(url, origin);
	}

	@GetMapping("page")
	HttpEntity<Page<FeedDto>> getPage(
		WebRequest request,
		@PageableDefault(sort = "modified", direction = Direction.DESC) Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) @Length(max = URL_LEN) String url,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		var result = feedService.page(
			RefFilter
				.builder()
				.query(query)
				.url(url)
				.modifiedAfter(modifiedAfter)
				.build(),
			pageable);
		if (!sortedByTime(pageable)) return ResponseEntity.ok(result);
		return ifModifiedSincePage(request, result);
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateFeed(
		@RequestBody @Valid Feed feed
	) {
		feedService.update(feed);
	}

	@PatchMapping(consumes = "application/json-patch+json")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void patchFeed(
		@RequestParam @Length(max = URL_LEN) @URL String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin,
		@RequestBody JsonPatch patch
	) {
		feedService.patch(url, origin, patch);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteFeed(
		@RequestParam @Length(max = URL_LEN) @URL String url
	) {
		feedService.delete(url);
	}

	@GetMapping("scrape")
	void scrape(
		@RequestParam @Length(max = URL_LEN) @URL String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin
	) throws FeedException, IOException {
		feedService.scrape(url, origin);
	}
}
