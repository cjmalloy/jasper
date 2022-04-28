package jasper.web.rest;

import java.time.Instant;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import jasper.domain.Origin;
import jasper.repository.filter.OriginFilter;
import jasper.service.OriginService;
import jasper.service.dto.OriginNameDto;
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

	@GetMapping("page")
	Page<Origin> getPage(
		@PageableDefault(sort = "origin") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = OriginFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return originService.page(
			OriginFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build(),
			pageable);
	}

	@GetMapping("list/names")
	Page<OriginNameDto> getOriginNames(
		@PageableDefault(sort = "origin") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = OriginFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return originService.pageNames(
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