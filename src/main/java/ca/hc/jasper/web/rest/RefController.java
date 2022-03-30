package ca.hc.jasper.web.rest;

import java.time.Instant;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.repository.filter.RefFilter;
import ca.hc.jasper.repository.filter.TagQuery;
import ca.hc.jasper.service.RefService;
import ca.hc.jasper.service.dto.RefDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/ref")
@Validated
public class RefController {

	@Autowired
	RefService refService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createRef(
		@RequestBody @Valid Ref ref
	) {
		refService.create(ref);
	}

	@GetMapping
	RefDto getRef(
		@RequestParam String url,
		@RequestParam(defaultValue = "") String origin
	) {
		return refService.get(url, origin);
	}

	@GetMapping("list")
	Page<RefDto> getRefs(
		@PageableDefault(direction = Direction.DESC, sort = "created") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagQuery.REGEX) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return refService.page(
			RefFilter.builder()
					 .modifiedAfter(modifiedAfter)
					 .query(query).build(),
			pageable);
	}

	@GetMapping("count")
	long countRefs(
		@RequestParam(required = false) @Pattern(regexp = TagQuery.REGEX) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return refService.count(
			RefFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build());
	}

	@GetMapping("responses")
	Page<RefDto> getResponses(
		@PageableDefault(direction = Direction.DESC, sort = "created") Pageable pageable,
		@RequestParam String url,
		@RequestParam(required = false) @Pattern(regexp = TagQuery.REGEX) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return refService.responses(
			url,
			RefFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build(),
			pageable);
	}

	@GetMapping("responses/count")
	long countResponses(
		@RequestParam String url,
		@RequestParam(required = false) @Pattern(regexp = TagQuery.REGEX) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return refService.countResponses(
			url,
			RefFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build());
	}

	@GetMapping("sources")
	Page<RefDto> getSources(
		@PageableDefault(direction = Direction.DESC, sort = "created") Pageable pageable,
		@RequestParam String url,
		@RequestParam(required = false) @Pattern(regexp = TagQuery.REGEX) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return refService.sources(
			url,
			RefFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build(),
			pageable);
	}

	@GetMapping("sources/count")
	long countSources(
		@RequestParam String url,
		@RequestParam(required = false) @Pattern(regexp = TagQuery.REGEX) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return refService.countSources(
			url,
			RefFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build());
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateRef(
		@RequestBody @Valid Ref ref
	) {
		refService.update(ref);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteRef(
		@RequestParam String url
	) {
		refService.delete(url);
	}
}
