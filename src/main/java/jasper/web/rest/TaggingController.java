package jasper.web.rest;

import javax.validation.constraints.Pattern;

import jasper.domain.*;
import jasper.service.TaggingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/tags")
@Validated
public class TaggingController {

	@Autowired
	TaggingService taggingService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createTags(
		@RequestParam @Pattern(regexp = TagId.REGEX) String tag,
		@RequestParam @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Pattern(regexp = Origin.REGEX) String origin
	) {
		taggingService.create(tag, url, origin);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteTags(
		@RequestParam @Pattern(regexp = TagId.REGEX) String tag,
		@RequestParam @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Pattern(regexp = Origin.REGEX) String origin
	) {
		taggingService.delete(tag, url, origin);
	}
}
