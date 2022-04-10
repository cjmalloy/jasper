package ca.hc.jasper.web.rest;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.repository.filter.RefFilter;
import ca.hc.jasper.service.RefService;
import ca.hc.jasper.service.dto.RefDto;
import ca.hc.jasper.service.errors.NotFoundException;
import com.github.fge.jsonpatch.JsonPatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
		@RequestParam String url,
		@RequestParam(defaultValue = "") String origin
	) {
		return refService.get(url, origin);
	}

	@GetMapping("list")
	List<RefDto> getList(
		@RequestParam String[] urls,
		@RequestParam(defaultValue = "") String origin
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
		@RequestParam String url,
		@RequestParam(defaultValue = "") String origin
	) {
		return refService.exists(url, origin);
	}

	@GetMapping("page")
	Page<RefDto> getPage(
		@PageableDefault(direction = Direction.DESC, sort = "created") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) String sources,
		@RequestParam(required = false) String responses,
		@RequestParam(required = false) boolean uncited,
		@RequestParam(required = false) boolean unsourced,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return refService.page(
			RefFilter.builder()
					 .query(query)
					 .sources(sources)
					 .responses(responses)
					 .uncited(uncited)
					 .unsourced(unsourced)
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
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return refService.count(
			RefFilter
				.builder()
				.query(query)
				.sources(sources)
				.responses(responses)
				.uncited(uncited)
				.unsourced(unsourced)
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
		@RequestParam String url,
		@RequestParam(defaultValue = "") String origin,
		@RequestBody JsonPatch patch
	) {
		refService.patch(url, origin, patch);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteRef(
		@RequestParam String url
	) {
		refService.delete(url);
	}
}
