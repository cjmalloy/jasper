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
import jakarta.validation.constraints.Size;
import jasper.component.HttpCache;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.domain.proj.HasOrigin;
import jasper.repository.filter.RefFilter;
import jasper.service.RefService;
import jasper.service.dto.RefDto;
import org.hibernate.validator.constraints.Length;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static jasper.domain.Ref.URL_LEN;
import static jasper.domain.proj.HasOrigin.ORIGIN_LEN;
import static jasper.domain.proj.Tag.TAG_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static jasper.repository.filter.Query.SEARCH_LEN;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.domain.Sort.Order.desc;
import static org.springframework.data.domain.Sort.by;

@RestController
@RequestMapping("api/v1/ref")
@Validated
@Tag(name = "Ref")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
})
public class RefController {

	@Autowired
	RefService refService;

	@Autowired
	HttpCache httpCache;

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	Instant createRef(
		@RequestBody @Valid Ref ref,
		@RequestParam(defaultValue = "false") boolean force
	) {
		return refService.create(ref, force);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "304", content = @Content()),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping
	HttpEntity<RefDto> getRef(
		WebRequest request,
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		return httpCache.ifNotModified(request, refService.get(url, origin));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping("page")
	HttpEntity<Page<RefDto>> getRefPage(
		WebRequest request,
		@PageableDefault @ParameterObject Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(required = false) boolean obsolete,
		@RequestParam(required = false) @Length(max = URL_LEN) @Pattern(regexp = Ref.SCHEME_REGEX) String scheme,
		@RequestParam(required = false) @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String sources,
		@RequestParam(required = false) @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String responses,
		@RequestParam(required = false) boolean untagged,
		@RequestParam(required = false) boolean uncited,
		@RequestParam(required = false) boolean unsourced,
		@RequestParam(required = false) Instant modifiedBefore,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(required = false) Instant publishedBefore,
		@RequestParam(required = false) Instant publishedAfter,
		@RequestParam(required = false) Instant createdBefore,
		@RequestParam(required = false) Instant createdAfter,
		@RequestParam(required = false) Instant responseBefore,
		@RequestParam(required = false) Instant responseAfter,
		@RequestParam(required = false) @Size(max = 100) List<@Length(max = TAG_LEN) @Pattern(regexp = Plugin.REGEX) String> pluginResponse,
		@RequestParam(required = false) @Size(max = 100) List<@Length(max = TAG_LEN) @Pattern(regexp = Plugin.REGEX) String> noPluginResponse,
		@RequestParam(required = false) @Length(max = SEARCH_LEN) String search
	) {
		if ("!@*".equals(query)) {
			ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(100, TimeUnit.DAYS).cachePublic())
				.body(Page.empty(pageable));
		}
		var rankedSort = false;
		if (pageable.getSort().isUnsorted() || pageable.getSort().getOrderFor("rank") != null) {
			if (isNotBlank(search)) {
				rankedSort = true;
			}
			if (pageable.getSort().isUnsorted()) {
				pageable = PageRequest.of(
					pageable.getPageNumber(),
					pageable.getPageSize(),
					by(desc(Ref_.MODIFIED)));
			} else {
				// Remove rank order
				pageable = PageRequest.of(
					pageable.getPageNumber(),
					pageable.getPageSize(),
					by(pageable.getSort()
						.stream()
						.filter(o -> !o.getProperty().equals("rank"))
						.toList()));
			}
		}
		return httpCache.ifNotModifiedPage(request, refService.page(
			RefFilter.builder()
				.url(url)
				.obsolete(obsolete)
				.scheme(scheme)
				.query(query)
				.search(search)
				.rankedOrder(rankedSort)
				.sources(sources)
				.responses(responses)
				.untagged(untagged)
				.uncited(uncited)
				.unsourced(unsourced)
				.pluginResponse(pluginResponse)
				.noPluginResponse(noPluginResponse)
				.modifiedBefore(modifiedBefore)
				.modifiedAfter(modifiedAfter)
				.publishedBefore(publishedBefore)
				.publishedAfter(publishedAfter)
				.createdBefore(createdBefore)
				.createdAfter(createdAfter)
				.responseBefore(responseBefore)
				.responseAfter(responseAfter).build(),
			pageable));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping("count")
	long countRefs(
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(required = false) boolean obsolete,
		@RequestParam(required = false) @Length(max = URL_LEN) @Pattern(regexp = Ref.SCHEME_REGEX) String scheme,
		@RequestParam(required = false) @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String sources,
		@RequestParam(required = false) @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String responses,
		@RequestParam(required = false) boolean untagged,
		@RequestParam(required = false) boolean uncited,
		@RequestParam(required = false) boolean unsourced,
		@RequestParam(required = false) Instant modifiedBefore,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(required = false) Instant publishedBefore,
		@RequestParam(required = false) Instant publishedAfter,
		@RequestParam(required = false) Instant createdBefore,
		@RequestParam(required = false) Instant createdAfter,
		@RequestParam(required = false) Instant responseBefore,
		@RequestParam(required = false) Instant responseAfter,
		@RequestParam(required = false) @Size(max = 100) List<@Length(max = TAG_LEN) @Pattern(regexp = Plugin.REGEX) String> pluginResponse,
		@RequestParam(required = false) @Size(max = 100) List<@Length(max = TAG_LEN) @Pattern(regexp = Plugin.REGEX) String> noPluginResponse,
		@RequestParam(required = false) @Length(max = SEARCH_LEN) String search
	) {
		return refService.count(
			RefFilter.builder()
				.query(query)
				.search(search)
				.url(url)
				.obsolete(obsolete)
				.scheme(scheme)
				.sources(sources)
				.responses(responses)
				.untagged(untagged)
				.uncited(uncited)
				.unsourced(unsourced)
				.pluginResponse(pluginResponse)
				.noPluginResponse(noPluginResponse)
				.modifiedBefore(modifiedBefore)
				.modifiedAfter(modifiedAfter)
				.publishedBefore(publishedBefore)
				.publishedAfter(publishedAfter)
				.createdBefore(createdBefore)
				.createdAfter(createdAfter)
				.responseBefore(responseBefore)
				.responseAfter(responseAfter).build());
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PutMapping
	Instant updateRef(
		@RequestBody @Valid Ref ref,
		@RequestParam(defaultValue = "false") boolean force
	) {
		return refService.update(ref, force);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PatchMapping(consumes = "application/json-patch+json")
	Instant patchRef(
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam Instant cursor,
		@RequestBody JsonPatch patch
	) {
		return refService.patch(url, origin, cursor, patch);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PatchMapping(consumes = "application/merge-patch+json")
	Instant mergeRef(
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam Instant cursor,
		@RequestBody JsonMergePatch patch
	) {
		return refService.patch(url, origin, cursor, patch);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteRef(
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		refService.delete(url, origin);
	}
}
