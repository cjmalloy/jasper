package jasper.web.rest;

import com.github.fge.jsonpatch.JsonPatch;
import jasper.domain.Ext;
import jasper.domain.TagId;
import jasper.repository.filter.TagFilter;
import jasper.service.ExtService;
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

import static jasper.domain.TagId.QTAG_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static jasper.util.RestUtil.ifModifiedSince;

@RestController
@RequestMapping("api/v1/ext")
@Validated
public class ExtController {

	@Autowired
	ExtService extService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createExt(
		@RequestBody @Valid Ext ext
	) {
		extService.create(ext);
	}

	@GetMapping
	HttpEntity<Ext> getExt(
		WebRequest request,
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = TagId.REGEX) String tag
	) {
		return ifModifiedSince(request, extService.get(tag));
	}

	@GetMapping("page")
	Page<Ext> getPage(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = TagFilter.QUERY) String query
	) {
		return extService.page(
			TagFilter
				.builder()
				.query(query).build(),
			pageable);
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateExt(
		@RequestBody @Valid Ext ext
	) {
		extService.update(ext);
	}

	@PatchMapping(consumes = "application/json-patch+json")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void patchExt(
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = TagId.REGEX) String tag,
		@RequestBody JsonPatch patch
	) {
		extService.patch(tag, patch);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteExt(
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = TagId.REGEX) String tag
	) {
		extService.delete(tag);
	}
}
