package ca.hc.jasper.web.rest;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.*;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.service.ExtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/ext")
@Validated
public class ExtController {

	@Autowired
	ExtService extService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createTag(
		@RequestBody @Valid Ext ext
	) {
		extService.create(ext);
	}

	@GetMapping
	Ext getTag(
		@RequestParam @Pattern(regexp = TagId.REGEX) String tag
	) {
		return extService.get(tag);
	}

	@GetMapping("list")
	Page<Ext> getTags(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagFilter.QUERY) String query
	) {
		return extService.page(
			TagFilter
				.builder()
				.query(query).build(),
			pageable);
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateTag(
		@RequestBody @Valid Ext ext
	) {
		extService.update(ext);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteTag(
		@RequestParam @Pattern(regexp = TagId.REGEX) String tag
	) {
		extService.delete(tag);
	}
}
