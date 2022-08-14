package jasper.web.rest;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Template;
import jasper.domain.proj.HasOrigin;
import jasper.repository.filter.RefFilter;
import jasper.repository.filter.TagFilter;
import jasper.repository.filter.TemplateFilter;
import jasper.service.ExtService;
import jasper.service.PluginService;
import jasper.service.RefService;
import jasper.service.TemplateService;
import jasper.service.UserService;
import jasper.service.dto.RefDto;
import jasper.service.dto.UserDto;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

import static jasper.domain.proj.HasOrigin.ORIGIN_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;

@RestController
@RequestMapping("api/v1/repl")
@Validated
@Tag(name = "Repl")
@ApiResponses({
	@ApiResponse(responseCode = "200"),
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
})
public class ReplicateController {

	@Autowired
	RefService refService;
	@Autowired
	ExtService extService;
	@Autowired
	PluginService pluginService;
	@Autowired
	TemplateService templateService;
	@Autowired
	UserService userService;

	@GetMapping("ref")
	List<RefDto> ref(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(defaultValue = "500") int size
	) {
		return refService.page(
				RefFilter.builder()
					.origin(origin)
					.query(query)
					.modifiedAfter(modifiedAfter)
					.build(),
				PageRequest.of(0, size, Direction.ASC, "modified"))
			.getContent();
	}

	@GetMapping("ext")
	List<Ext> ext(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(defaultValue = "500") int size
	) {
		return extService.page(
				TagFilter.builder()
					.origin(origin)
					.query(query)
					.modifiedAfter(modifiedAfter)
					.build(),
				PageRequest.of(0, size, Direction.ASC, "modified"))
			.getContent();
	}

	@GetMapping("user")
	List<UserDto> user(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(defaultValue = "500") int size
	) {
		return userService.page(
				TagFilter.builder()
					.origin(origin)
					.query(query)
					.modifiedAfter(modifiedAfter)
					.build(),
				PageRequest.of(0, size, Direction.ASC, "modified"))
			.getContent();
	}

	@GetMapping("plugin")
	List<Plugin> plugin(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(defaultValue = "500") int size
	) {
		return pluginService.page(
				TagFilter.builder()
					.origin(origin)
					.query(query)
					.modifiedAfter(modifiedAfter)
					.build(),
				PageRequest.of(0, size, Direction.ASC, "modified"))
			.getContent();
	}

	@GetMapping("template")
	List<Template> template(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(defaultValue = "500") int size
	) {
		return templateService.page(
				TemplateFilter.builder()
					.origin(origin)
					.query(query)
					.modifiedAfter(modifiedAfter)
					.build(),
				PageRequest.of(0, size, Direction.ASC, "modified"))
			.getContent();
	}
}
