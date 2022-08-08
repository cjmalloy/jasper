package jasper.web.rest;

import com.github.fge.jsonpatch.JsonPatch;
import jasper.domain.Origin;
import jasper.domain.Ref;
import jasper.errors.NotFoundException;
import jasper.repository.filter.RefFilter;
import jasper.service.RefService;
import jasper.service.dto.RefDto;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

import static jasper.domain.Origin.ORIGIN_LEN;
import static jasper.domain.Ref.SEARCH_LEN;
import static jasper.domain.Ref.URL_LEN;
import static jasper.domain.TagId.TAG_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static jasper.util.RestUtil.ifModifiedSince;
import static jasper.util.RestUtil.ifModifiedSinceList;

@RestController
@RequestMapping("api/v1/ref")
@Validated
public class RefController {

	@Autowired
	RefService refService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createRef(
		@RequestBody @Valid Ref ref
	) {
		refService.create(ref);
	}

	@GetMapping
	HttpEntity<RefDto> getRef(
		WebRequest request,
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin
	) {
		return ifModifiedSince(request, refService.get(url, origin));
	}

	@GetMapping("exists")
	boolean refExists(
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin
	) {
		return refService.exists(url, origin);
	}

	@GetMapping("list")
	HttpEntity<List<RefDto>> getList(
		WebRequest request,
		@RequestParam @Size(max = 100) List<@Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String> urls,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin
	) {
		return ifModifiedSinceList(request, urls.stream().map(url -> {
			try {
				return refService.get(url, origin);
			} catch (NotFoundException | AccessDeniedException e) {
				return null;
			}
		}).toList());
	}

	@GetMapping("page")
	Page<RefDto> getPage(
		@PageableDefault Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) @Length(max = URL_LEN) String url,
		@RequestParam(required = false) @Length(max = URL_LEN) String sources,
		@RequestParam(required = false) @Length(max = URL_LEN) String responses,
		@RequestParam(required = false) boolean uncited,
		@RequestParam(required = false) boolean unsourced,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(required = false) @Length(max = TAG_LEN) String pluginResponse,
		@RequestParam(required = false) @Length(max = TAG_LEN) String noPluginResponse,
		@RequestParam(required = false) @Length(max = SEARCH_LEN) String search
	) {
		var rankedSort = false;
		if (pageable.getSort().isUnsorted() || pageable.getSort().getOrderFor("rank") != null) {
			if (search != null && !search.isBlank()) {
				rankedSort = true;
				pageable = PageRequest.of(
					pageable.getPageNumber(),
					pageable.getPageSize());
			} else {
				pageable = PageRequest.of(
					pageable.getPageNumber(),
					pageable.getPageSize(),
					Sort.by(
						Direction.DESC,
						"created"));
			}
		}
		return refService.page(
			RefFilter.builder()
					 .url(url)
					 .query(query)
					 .search(search)
					 .rankedOrder(rankedSort)
					 .sources(sources)
					 .responses(responses)
					 .uncited(uncited)
					 .unsourced(unsourced)
					 .pluginResponse(pluginResponse)
					 .noPluginResponse(noPluginResponse)
					 .modifiedAfter(modifiedAfter).build(),
			pageable);
	}

	@GetMapping("count")
	long countRefs(
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) @Length(max = URL_LEN) String sources,
		@RequestParam(required = false) @Length(max = URL_LEN) String responses,
		@RequestParam(required = false) boolean uncited,
		@RequestParam(required = false) boolean unsourced,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(required = false) @Length(max = TAG_LEN) String pluginResponse,
		@RequestParam(required = false) @Length(max = TAG_LEN) String noPluginResponse,
		@RequestParam(required = false) @Length(max = SEARCH_LEN) String search
	) {
		return refService.count(
			RefFilter
				.builder()
				.query(query)
				.search(search)
				.sources(sources)
				.responses(responses)
				.uncited(uncited)
				.unsourced(unsourced)
				.pluginResponse(pluginResponse)
				.noPluginResponse(noPluginResponse)
				.modifiedAfter(modifiedAfter).build());
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateRef(
		@RequestBody @Valid Ref ref
	) {
		refService.update(ref);
	}

	@PatchMapping(consumes = "application/json-patch+json")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void patchRef(
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin,
		@RequestBody JsonPatch patch
	) {
		refService.patch(url, origin, patch);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteRef(
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url
	) {
		refService.delete(url);
	}
}
