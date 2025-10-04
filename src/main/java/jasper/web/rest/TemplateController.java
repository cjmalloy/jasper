package jasper.web.rest;

import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jasper.component.HttpCache;
import jasper.domain.Template;
import jasper.repository.filter.TagFilter;
import jasper.service.TemplateService;
import jasper.service.dto.TemplateDto;
import org.hibernate.validator.constraints.Length;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

import static jasper.domain.proj.Tag.QTAG_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static jasper.repository.filter.Query.SEARCH_LEN;

@RestController
@RequestMapping("api/v1/template")
@Validated
@Tag(name = "Template")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
})
public class TemplateController {

	@Autowired
	TemplateService templateService;

	@Autowired
	HttpCache httpCache;

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Instant createTemplate(
		@RequestBody @Valid Template template
	) {
		return templateService.create(template);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping
	HttpEntity<TemplateDto> getTemplate(
		@RequestParam(defaultValue = "") @Length(max = QTAG_LEN) @Pattern(regexp = Template.QTAG_REGEX) String tag
	) {
		return httpCache.ifNotModified(templateService.get(tag));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping("page")
	HttpEntity<Page<TemplateDto>> getTemplatePage(
		@PageableDefault(sort = "tag") @ParameterObject Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = TagFilter.QUERY) String query,
		@RequestParam(required = false) Integer nesting,
		@RequestParam(required = false) Integer level,
		@RequestParam(required = false) Boolean deleted,
		@RequestParam(required = false) Instant modifiedBefore,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(required = false) @Length(max = SEARCH_LEN) String search
	) {
		return httpCache.ifNotModifiedPage(templateService.page(
			TagFilter.builder()
				.query(query)
				.nesting(nesting)
				.level(level)
				.deleted(deleted)
				.search(search)
				.modifiedBefore(modifiedBefore)
				.modifiedAfter(modifiedAfter)
				.build(),
			pageable));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PutMapping
	Instant updateTemplate(
		@RequestBody @Valid Template template
	) {
		return templateService.update(template);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PatchMapping(consumes = "application/json-patch+json")
	Instant patchTemplate(
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = jasper.domain.proj.Tag.QTAG_REGEX) String tag,
		@RequestParam Instant cursor,
		@RequestBody JsonPatch patch
	) {
		return templateService.patch(tag, cursor, patch);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PatchMapping(consumes = "application/merge-patch+json")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	Instant mergeTemplate(
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = jasper.domain.proj.Tag.QTAG_REGEX) String tag,
		@RequestParam Instant cursor,
		@RequestBody JsonMergePatch patch
	) {
		return templateService.patch(tag, cursor, patch);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteTemplate(
		@RequestParam(defaultValue = "") @Length(max = QTAG_LEN) @Pattern(regexp = Template.QTAG_REGEX) String tag
	) {
		templateService.delete(tag);
	}
}
