package ca.hc.jasper.web.rest;

import ca.hc.jasper.domain.Tag;
import ca.hc.jasper.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/tag")
@Validated
public class TagController {

	@Autowired
	TagService tagService;

	@PostMapping
	void createTag(
		@RequestBody Tag tag
	) {
		tagService.create(tag);
	}

	@GetMapping
	Tag getTag(
		@RequestParam String tag,
		@RequestParam(defaultValue = "") String origin
	) {
		return tagService.get(tag, origin);
	}

	@GetMapping("list")
	Page<Tag> getTags(
		@PageableDefault(sort = "tag") Pageable pageable
	) {
		return tagService.page(pageable);
	}

	@PutMapping
	void updateTag(
		@RequestBody Tag tag
	) {
		tagService.update(tag);
	}

	@DeleteMapping
	void deleteTag(
		@RequestParam String tag
	) {
		tagService.delete(tag);
	}
}
