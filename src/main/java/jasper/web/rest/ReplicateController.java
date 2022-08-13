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
import jasper.repository.ExtRepository;
import jasper.repository.PluginRepository;
import jasper.repository.RefRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.UserRepository;
import jasper.repository.filter.RefFilter;
import jasper.repository.filter.TagFilter;
import jasper.repository.filter.TemplateFilter;
import jasper.service.dto.DtoMapper;
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
	RefRepository refRepository;
	@Autowired
	ExtRepository extRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	PluginRepository pluginRepository;
	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	DtoMapper mapper;

	@GetMapping("ref")
	List<RefDto> ref(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(defaultValue = "500") int size
	) {
		return refRepository.findAll(
				RefFilter.builder()
					.origin(origin)
					.query(query)
					.modifiedAfter(modifiedAfter)
					.build()
					.spec(),
				PageRequest.of(0, size, Direction.ASC, "modified"))
			.map(mapper::domainToDto)
			.getContent();
	}

	@GetMapping("ext")
	List<Ext> ext(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(defaultValue = "500") int size
	) {
		return extRepository.findAll(
				TagFilter.builder()
					.origin(origin)
					.query(query)
					.modifiedAfter(modifiedAfter)
					.build()
					.spec(),
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
		return userRepository.findAll(
				TagFilter.builder()
					.origin(origin)
					.query(query)
					.modifiedAfter(modifiedAfter)
					.build()
					.spec(),
				PageRequest.of(0, size, Direction.ASC, "modified"))
			.map(mapper::domainToDto)
			.getContent();
	}

	@GetMapping("plugin")
	List<Plugin> plugin(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(defaultValue = "500") int size
	) {
		return pluginRepository.findAll(
				TagFilter.builder()
					.origin(origin)
					.query(query)
					.modifiedAfter(modifiedAfter)
					.build()
					.spec(),
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
		return templateRepository.findAll(
				TemplateFilter.builder()
					.origin(origin)
					.query(query)
					.modifiedAfter(modifiedAfter)
					.build()
					.spec(),
				PageRequest.of(0, size, Direction.ASC, "modified"))
			.getContent();
	}
}
