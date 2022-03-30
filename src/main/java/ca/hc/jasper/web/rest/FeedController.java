package ca.hc.jasper.web.rest;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.Feed;
import ca.hc.jasper.repository.filter.RefFilter;
import ca.hc.jasper.repository.filter.TagQuery;
import ca.hc.jasper.service.FeedService;
import ca.hc.jasper.service.dto.FeedDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
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
		@RequestParam String url,
		@RequestParam(defaultValue = "") String origin
	) {
		return feedService.get(url, origin);
	}

	@GetMapping("list")
	Page<FeedDto> getFeeds(
		@PageableDefault(direction = Direction.DESC, sort = "created") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagQuery.REGEX) String query
	) {
		return feedService.page(
			RefFilter.builder()
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

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteFeed(
		@RequestParam String url
	) {
		feedService.delete(url);
	}
}
