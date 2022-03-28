package ca.hc.jasper.web.rest;

import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.Origin;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.repository.filter.TagList;
import ca.hc.jasper.service.OriginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/origin")
@Validated
public class OriginController {

	@Autowired
	OriginService originService;

	@PostMapping
	void createTag(
		@RequestBody Origin origin
	) {
		originService.create(origin);
	}

	@GetMapping
	Origin getTag(
		@RequestParam String tag
	) {
		return originService.get(tag);
	}

	@GetMapping("list")
	Page<Origin> getTags(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagList.REGEX) String query
	) {
		return originService.page(
			TagFilter.builder()
				.query(query).build(),
			pageable);
	}

	@PutMapping
	void updateTag(
		@RequestBody Origin origin
	) {
		originService.update(origin);
	}

	@DeleteMapping
	void deleteTag(
		@RequestParam String tag
	) {
		originService.delete(tag);
	}
}
