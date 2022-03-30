package ca.hc.jasper.web.rest;

import java.time.Instant;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.Origin;
import ca.hc.jasper.repository.filter.*;
import ca.hc.jasper.repository.filter.TagList;
import ca.hc.jasper.service.OriginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/origin")
@Validated
public class OriginController {

	@Autowired
	OriginService originService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createOrigin(
		@RequestBody @Valid Origin origin
	) {
		originService.create(origin);
	}

	@GetMapping
	Origin getOrigin(
		@RequestParam @Pattern(regexp = Origin.REGEX_NOT_BLANK) String origin
	) {
		return originService.get(origin);
	}

	@GetMapping("list")
	Page<Origin> getOrigins(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagList.REGEX) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return originService.page(
			OriginFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build(),
			pageable);
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateOrigin(
		@RequestBody @Valid Origin origin
	) {
		originService.update(origin);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteOrigin(
		@RequestParam @Pattern(regexp = Origin.REGEX_NOT_BLANK) String tag
	) {
		originService.delete(tag);
	}
}
