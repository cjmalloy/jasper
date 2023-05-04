package jasper.web.rest;

import com.github.fge.jsonpatch.JsonPatch;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jasper.domain.Ext;
import jasper.domain.proj.Tag;
import jasper.repository.filter.TagFilter;
import jasper.service.ExtService;
import jasper.service.dto.ExtDto;
import org.hibernate.validator.constraints.Length;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import java.time.Instant;

import static jasper.domain.proj.Tag.QTAG_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static jasper.repository.filter.Query.SEARCH_LEN;
import static jasper.util.RestUtil.ifNotModified;
import static jasper.util.RestUtil.ifNotModifiedPage;

@RestController
@RequestMapping("api/v1/ext")
@Validated
@io.swagger.v3.oas.annotations.tags.Tag(name = "Ext")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
})
public class ExtController {

	@Autowired
	ExtService extService;

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createExt(
		@RequestBody @Valid Ext ext
	) {
		extService.create(ext);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping
	HttpEntity<ExtDto> getExt(
		WebRequest request,
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = Tag.QTAG_REGEX) String tag
	) {
		return ifNotModified(request, extService.get(tag));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
	})
	@GetMapping("page")
	HttpEntity<Page<ExtDto>> getExtPage(
		WebRequest request,
		@PageableDefault(sort = "tag") @ParameterObject Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = TagFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(required = false) Instant modifiedBefore,
		@RequestParam(required = false) @Length(max = SEARCH_LEN) String search
	) {
		return ifNotModifiedPage(request, extService.page(
			TagFilter.builder()
				.query(query)
				.search(search)
				.modifiedBefore(modifiedBefore)
				.modifiedAfter(modifiedAfter).build(),
			pageable));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
	})
	@GetMapping("count")
	long countExts(
		WebRequest request,
		@PageableDefault(sort = "tag") @ParameterObject Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = TagFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(required = false) Instant modifiedBefore,
		@RequestParam(required = false) @Length(max = SEARCH_LEN) String search
	) {
		return extService.count(
			TagFilter.builder()
				.query(query)
				.search(search)
				.modifiedBefore(modifiedBefore)
				.modifiedAfter(modifiedAfter).build());
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateExt(
		@RequestBody @Valid Ext ext
	) {
		extService.update(ext);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PatchMapping(consumes = "application/json-patch+json")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void patchExt(
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = Tag.QTAG_REGEX) String tag,
		@RequestBody JsonPatch patch
	) {
		extService.patch(tag, patch);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteExt(
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = Tag.QTAG_REGEX) String tag
	) {
		extService.delete(tag);
	}
}
