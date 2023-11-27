package jasper.web.rest;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
import jasper.domain.proj.HasOrigin;
import jasper.repository.filter.RefFilter;
import jasper.repository.filter.TagFilter;
import jasper.repository.filter.TemplateFilter;
import jasper.service.ExtService;
import jasper.service.PluginService;
import jasper.service.RefService;
import jasper.service.ReplicateService;
import jasper.service.TemplateService;
import jasper.service.UserService;
import jasper.service.dto.ExtDto;
import jasper.service.dto.PluginDto;
import jasper.service.dto.RefReplDto;
import jasper.service.dto.TemplateDto;
import jasper.service.dto.UserDto;
import org.hibernate.validator.constraints.Length;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;

import static jasper.domain.proj.HasOrigin.ORIGIN_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;

@CrossOrigin
@RestController
@RequestMapping("api/v1/repl")
@Validated
@Tag(name = "Repl")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
})
public class ReplicateController {
	private static final Logger logger = LoggerFactory.getLogger(ReplicateController.class);

	@Autowired
	ReplicateService replService;
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

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("ref")
	List<RefReplDto> ref(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = RefFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter,
		@RequestParam(defaultValue = "500") int size
	) {
		return replService.page(
				RefFilter.builder()
					.origin(origin)
					.query(query)
					.modifiedAfter(modifiedAfter)
					.build(),
				PageRequest.of(0, size, Direction.ASC, "modified"))
			.getContent();
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("ref/cursor")
	Instant refCursor(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		return refService.cursor(origin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@PostMapping("ref")
	void refPush(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestBody @Valid List<Ref> refs
	) {
		logger.debug("Receiving push of {} refs", refs.size());
		RuntimeException first = null;
		for (var ref : refs) {
			try {
				ref.setOrigin(origin);
				refService.push(ref);
			} catch (RuntimeException e) {
				first = first == null ? e : first;
			}
		}
		if (first != null) throw first;
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("ext")
	List<ExtDto> ext(
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

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("ext/cursor")
	Instant extCursor(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		return extService.cursor(origin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@PostMapping("ext")
	void extPush(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestBody @Valid List<Ext> exts
	) {
		logger.debug("Receiving push of {} exts", exts.size());
		RuntimeException first = null;
		for (var ext : exts) {
			try {
				ext.setOrigin(origin);
				extService.push(ext);
			} catch (RuntimeException e) {
				first = first == null ? e : first;
			}
		}
		if (first != null) throw first;
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
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

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("user/cursor")
	Instant userCursor(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		return userService.cursor(origin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@PostMapping("user")
	void userPush(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestBody @Valid List<User> users
	) {
		logger.debug("Receiving push of {} users", users.size());
		RuntimeException first = null;
		for (var user : users) {
			try {
				user.setOrigin(origin);
				userService.push(user);
			} catch (RuntimeException e) {
				first = first == null ? e : first;
			}
		}
		if (first != null) throw first;
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("plugin")
	List<PluginDto> plugin(
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

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("plugin/cursor")
	Instant pluginCursor(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		return pluginService.cursor(origin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@PostMapping("plugin")
	void pluginPush(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestBody @Valid List<Plugin> plugins
	) {
		logger.debug("Receiving push of {} plugins", plugins.size());
		RuntimeException first = null;
		for (var plugin : plugins) {
			try {
				plugin.setOrigin(origin);
				pluginService.push(plugin);
			} catch (RuntimeException e) {
				first = first == null ? e : first;
			}
		}
		if (first != null) throw first;
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("template")
	List<TemplateDto> template(
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

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("template/cursor")
	Instant templateCursor(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin
	) {
		return templateService.cursor(origin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@PostMapping("template")
	void templatePush(
		@RequestParam(defaultValue = "") @Length(max = ORIGIN_LEN) @Pattern(regexp = HasOrigin.REGEX) String origin,
		@RequestBody @Valid List<Template> templates
	) {
		logger.debug("Receiving push of {} templates", templates.size());
		RuntimeException first = null;
		for (var template : templates) {
			try {
				template.setOrigin(origin);
				templateService.push(template);
			} catch (RuntimeException e) {
				first = first == null ? e : first;
			}
		}
		if (first != null) throw first;
	}
}
