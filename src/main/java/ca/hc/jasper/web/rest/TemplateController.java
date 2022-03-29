package ca.hc.jasper.web.rest;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.Template;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.repository.filter.TagList;
import ca.hc.jasper.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/template")
@Validated
public class TemplateController {

	@Autowired
	TemplateService templateService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createPlugin(
		@Valid @RequestBody Template template
	) {
		templateService.create(template);
	}

	@GetMapping
	Template getPlugin(
		@RequestParam String tag
	) {
		return templateService.get(tag);
	}

	@GetMapping("list")
	Page<Template> getPlugins(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagList.REGEX) String query
	) {
		return templateService.page(
			TagFilter.builder()
				.query(query).build(),
			pageable);
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updatePlugin(
		@Valid @RequestBody Template template
	) {
		templateService.update(template);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deletePlugin(
		@Valid @RequestParam String tag
	) {
		templateService.delete(tag);
	}
}
