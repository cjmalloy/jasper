package jasper.web.rest;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jasper.domain.Ref;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.Tag;
import jasper.service.TaggingService;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Pattern;
import java.util.List;

import static jasper.domain.Ref.URL_LEN;
import static jasper.domain.proj.HasOrigin.ORIGIN_LEN;
import static jasper.domain.proj.Tag.TAG_LEN;

@RestController
@RequestMapping("api/v1/tags")
@Validated
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tagging")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
})
public class TaggingController {

	@Autowired
	TaggingService taggingService;

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
	})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createTags(
		@RequestParam @Length(max = TAG_LEN) @Pattern(regexp = Tag.REGEX) String tag,
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		taggingService.create(tag, url, origin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteTags(
		@RequestParam @Length(max = TAG_LEN) @Pattern(regexp = Tag.REGEX) String tag,
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		taggingService.delete(tag, url, origin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@PatchMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void patchTags(
		@RequestParam List<@Length(max = TAG_LEN) @Pattern(regexp = Tag.ADD_REMOVE_REGEX) String> tags,
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url,
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		taggingService.tag(tags, url, origin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
	})
	@PostMapping("response")
	@ResponseStatus(HttpStatus.CREATED)
	void createResponse(
		@RequestParam @Length(max = TAG_LEN) @Pattern(regexp = Tag.REGEX) String tag,
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url
	) {
		taggingService.createResponse(tag, url);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@DeleteMapping("response")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteResponse(
		@RequestParam @Length(max = TAG_LEN) @Pattern(regexp = Tag.REGEX) String tag,
		@RequestParam @Length(max = URL_LEN) @Pattern(regexp = Ref.REGEX) String url
	) {
		taggingService.deleteResponse(tag, url);
	}
}
