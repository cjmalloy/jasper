package jasper.web.rest;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import jasper.domain.Origin;
import jasper.domain.Ref;
import jasper.errors.NotFoundException;
import jasper.repository.filter.RefFilter;
import jasper.service.RefService;
import jasper.service.dto.RefDto;
import com.github.fge.jsonpatch.JsonPatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
	RefDto getRef(
		@RequestParam @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Pattern(regexp = Origin.REGEX) String origin
	) {
		return refService.get(url, origin);
	}

	@GetMapping("list")
	List<RefDto> getList(
		@RequestParam String[] urls, //TODO validate
		@RequestParam(defaultValue = "") @Pattern(regexp = Origin.REGEX) String origin
	) {
		return Arrays.stream(urls).map(url -> {
			try {
				return refService.get(url, origin);
			} catch (NotFoundException | AccessDeniedException e) {
				return null;
			}
		}).toList();
	}

	@GetMapping("exists")
	boolean refExists(
		@RequestParam @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Pattern(regexp = Origin.REGEX) String origin
	) {
		return refService.exists(url, origin);
	}

	@GetMapping("page")
	Page<RefDto> getPage(
		@PageableDefault Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) String sources,
		@RequestParam(required = false) String responses,
		@RequestParam(required = false) boolean uncited,
		@RequestParam(required = false) boolean unsourced,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(required = false) String pluginResponse,
		@RequestParam(required = false) String noPluginResponse,
		@RequestParam(required = false) String search
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
		@RequestParam(required = false) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) String sources,
		@RequestParam(required = false) String responses,
		@RequestParam(required = false) boolean uncited,
		@RequestParam(required = false) boolean unsourced,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(required = false) String pluginResponse,
		@RequestParam(required = false) String noPluginResponse,
		@RequestParam(required = false) String search
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
		@RequestParam @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Pattern(regexp = Origin.REGEX) String origin,
		@RequestBody JsonPatch patch
	) {
		refService.patch(url, origin, patch);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteRef(
		@RequestParam @Pattern(regexp = Ref.REGEX) String url
	) {
		refService.delete(url);
	}
}
