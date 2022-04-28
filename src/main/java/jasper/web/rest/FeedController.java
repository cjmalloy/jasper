package jasper.web.rest;

import java.io.IOException;
import java.time.Instant;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import com.github.fge.jsonpatch.JsonPatch;
import com.rometools.rome.io.FeedException;
import jasper.domain.Feed;
import jasper.domain.Origin;
import jasper.repository.filter.RefFilter;
import jasper.service.FeedService;
import jasper.service.dto.FeedDto;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
	FeedDto getFeed(
		@RequestParam @URL String url,
		@RequestParam(defaultValue = "") String origin
	) {
		return feedService.get(url, origin);
	}

	@GetMapping("page")
	Page<FeedDto> getPage(
		@PageableDefault(sort = "url") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return feedService.page(
			RefFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build(),
			pageable);
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
		@RequestParam @URL String url,
		@RequestParam(defaultValue = "") @Pattern(regexp = Origin.REGEX) String origin,
		@RequestBody JsonPatch patch
	) {
		feedService.patch(url, origin, patch);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteFeed(
		@RequestParam @URL String url
	) {
		feedService.delete(url);
	}

	@GetMapping("scrape")
	void scrape(
		@RequestParam @URL String url,
		@RequestParam(defaultValue = "") @Pattern(regexp = Origin.REGEX) String origin
	) throws FeedException, IOException {
		feedService.scrape(url, origin);
	}
}
