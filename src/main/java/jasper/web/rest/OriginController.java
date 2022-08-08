package jasper.web.rest;

import jasper.domain.Origin;
import jasper.repository.filter.OriginFilter;
import jasper.service.OriginService;
import jasper.service.dto.OriginNameDto;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
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
import java.time.Instant;

import static jasper.domain.Origin.ORIGIN_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static jasper.util.RestUtil.ifModifiedSince;

@RestController
@RequestMapping("api/v1/origin")
@Validated
public class OriginController {

	@Autowired
	OriginService originService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createOrigin(
		@RequestBody @Valid Origin origin
	) {
		originService.create(origin);
	}

	@GetMapping
	HttpEntity<Origin> getOrigin(
		WebRequest request,
		@RequestParam @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin
	) {
		return ifModifiedSince(request, originService.get(origin));
	}

	@GetMapping("page")
	Page<Origin> getPage(
		@PageableDefault(sort = "origin") Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = OriginFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return originService.page(
			OriginFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build(),
			pageable);
	}

	@GetMapping("list/names")
	Page<OriginNameDto> getOriginNames(
		@PageableDefault(sort = "origin") Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = OriginFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return originService.pageNames(
			OriginFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build(),
			pageable);
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateOrigin(
		@RequestBody @Valid Origin origin
	) {
		originService.update(origin);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteOrigin(
		@RequestParam @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin
	) {
		originService.delete(origin);
	}
}
