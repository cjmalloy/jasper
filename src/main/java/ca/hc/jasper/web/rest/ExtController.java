package ca.hc.jasper.web.rest;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.Ext;
import ca.hc.jasper.domain.TagId;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.service.ExtService;
import com.github.fge.jsonpatch.JsonPatch;
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
	void createExt(
		@RequestBody @Valid Ext ext
	) {
		extService.create(ext);
	}

	@GetMapping
	Ext getExt(
		@RequestParam @Pattern(regexp = TagId.REGEX) String tag
	) {
		return extService.get(tag);
	}

	@GetMapping("page")
	Page<Ext> getPage(
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
	void updateExt(
		@RequestBody @Valid Ext ext
	) {
		extService.update(ext);
	}

	@PatchMapping(consumes = "application/json-patch+json")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void patchExt(
		@RequestParam @Pattern(regexp = TagId.REGEX) String tag,
		@RequestBody JsonPatch patch
	) {
		extService.patch(tag, patch);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteExt(
		@RequestParam @Pattern(regexp = TagId.REGEX) String tag
	) {
		extService.delete(tag);
	}
}
