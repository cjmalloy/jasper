package jasper.web.rest;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jasper.domain.Plugin;
import jasper.errors.NotFoundException;
import jasper.repository.filter.TagFilter;
import jasper.service.PluginService;
import org.hibernate.validator.constraints.Length;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

import static jasper.domain.proj.IsTag.QTAG_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static jasper.util.RestUtil.ifModifiedSince;
import static jasper.util.RestUtil.ifModifiedSinceList;

@RestController
@RequestMapping("api/v1/plugin")
@Validated
@Tag(name = "Plugin")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	@ApiResponse(responseCode = "403", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
})
public class PluginController {

	@Autowired
	PluginService pluginService;

	@ApiResponses({
		@ApiResponse(responseCode = "201"),
		@ApiResponse(responseCode = "409", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createPlugin(
		@RequestBody @Valid Plugin plugin
	) {
		pluginService.create(plugin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
		@ApiResponse(responseCode = "304", content = @Content()),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@GetMapping
	HttpEntity<Plugin> getPlugin(
		WebRequest request,
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = Plugin.QTAG_REGEX) String tag
	) {
		return ifModifiedSince(request, pluginService.get(tag));
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("list")
	HttpEntity<List<Plugin>> getPluginList(
		WebRequest request,
		@RequestParam @Size(max = 100) List<@Length(max = QTAG_LEN) @Pattern(regexp = Plugin.QTAG_REGEX) String> tags
	) {
		return ifModifiedSinceList(request, tags.stream().map(tag -> {
			try {
				return pluginService.get(tag);
			} catch (NotFoundException | AccessDeniedException e) {
				return null;
			}
		}).toList());
	}

	@ApiResponses({
		@ApiResponse(responseCode = "200"),
	})
	@GetMapping("page")
	Page<Plugin> getPluginPage(
		@PageableDefault(sort = "tag") @ParameterObject Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = TagFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return pluginService.page(
			TagFilter.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build(),
			pageable);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
		@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(ref = "https://opensource.zalando.com/problem/schema.yaml#/Problem"))),
	})
	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updatePlugin(
		@RequestBody @Valid Plugin plugin
	) {
		pluginService.update(plugin);
	}

	@ApiResponses({
		@ApiResponse(responseCode = "204"),
	})
	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deletePlugin(
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = Plugin.QTAG_REGEX) String tag
	) {
		pluginService.delete(tag);
	}
}
