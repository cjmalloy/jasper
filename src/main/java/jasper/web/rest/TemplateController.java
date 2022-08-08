package jasper.web.rest;

import jasper.domain.Template;
import jasper.errors.NotFoundException;
import jasper.repository.filter.TagFilter;
import jasper.repository.filter.TemplateFilter;
import jasper.service.TemplateService;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

import static jasper.domain.TagId.QTAG_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static jasper.util.RestUtil.ifModifiedSince;
import static jasper.util.RestUtil.ifModifiedSinceList;
import static jasper.util.RestUtil.ifModifiedSincePage;
import static jasper.util.RestUtil.sortedByTime;

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
	HttpEntity<Template> getTemplate(
		WebRequest request,
		@RequestParam(defaultValue = "") @Length(max = QTAG_LEN) @Pattern(regexp = Template.REGEX) String tag
	) {
		return ifModifiedSince(request, templateService.get(tag));
	}

	@GetMapping("exists")
	boolean templateExists(
		@RequestParam(defaultValue = "") @Length(max = QTAG_LEN) @Pattern(regexp = Template.REGEX) String tag
	) {
		return templateService.exists(tag);
	}

	@GetMapping("list")
	HttpEntity<List<Template>> getList(
		WebRequest request,
		@RequestParam @Size(max = 100) List<@Length(max = QTAG_LEN) @Pattern(regexp = Template.REGEX) String> tags
	) {
		return ifModifiedSinceList(request, tags.stream().map(tag -> {
			try {
				return templateService.get(tag);
			} catch (NotFoundException | AccessDeniedException e) {
				return null;
			}
		}).toList());
	}

	@GetMapping("page")
	HttpEntity<Page<Template>> getPage(
		WebRequest request,
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = TagFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		var result = templateService.page(
			TemplateFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build(),
			pageable);
		if (!sortedByTime(pageable)) return ResponseEntity.ok(result);
		return ifModifiedSincePage(request, result);
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
		@RequestParam(defaultValue = "") @Length(max = QTAG_LEN) @Pattern(regexp = Template.REGEX) String tag
	) {
		templateService.delete(tag);
	}
}
