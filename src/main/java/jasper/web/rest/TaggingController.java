package jasper.web.rest;

import jasper.domain.Origin;
import jasper.domain.Ref;
import jasper.domain.TagId;
import jasper.service.TaggingService;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Pattern;

import static jasper.domain.Origin.ORIGIN_LEN;
import static jasper.domain.Ref.URL_LEN;
import static jasper.domain.TagId.TAG_LEN;

@RestController
@RequestMapping("api/v1/tags")
@Validated
public class TaggingController {

	@Autowired
	TaggingService taggingService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createTags(
		@RequestParam @Length(max = TAG_LEN) @Pattern(regexp = TagId.REGEX) String tag,
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin
	) {
		taggingService.create(tag, url, origin);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteTags(
		@RequestParam @Length(max = TAG_LEN) @Pattern(regexp = TagId.REGEX) String tag,
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = Origin.REGEX) String origin
	) {
		taggingService.delete(tag, url, origin);
	}
}
