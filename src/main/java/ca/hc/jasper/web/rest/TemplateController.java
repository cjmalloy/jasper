package ca.hc.jasper.web.rest;

import java.time.Instant;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.Template;
import ca.hc.jasper.repository.filter.TagFilter;
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
	void createTemplate(
		@RequestBody @Valid Template template
	) {
		templateService.create(template);
	}

	@GetMapping
	Template getTemplate(
		@RequestParam String tag
	) {
		return templateService.get(tag);
	}

	@GetMapping("list")
	Page<Template> getTemplates(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return templateService.page(
			TagFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build(),
			pageable);
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateTemplate(
		@RequestBody @Valid Template template
	) {
		templateService.update(template);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteTemplate(
		@RequestParam String tag
	) {
		templateService.delete(tag);
	}
}
