package jasper.web.rest;

import jasper.domain.Plugin;
import jasper.errors.NotFoundException;
import jasper.repository.filter.TagFilter;
import jasper.service.PluginService;
import org.hibernate.validator.constraints.Length;
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

import static jasper.domain.TagId.QTAG_LEN;
import static jasper.repository.filter.Query.QUERY_LEN;
import static jasper.util.RestUtil.ifModifiedSince;
import static jasper.util.RestUtil.ifModifiedSinceList;

@RestController
@RequestMapping("api/v1/plugin")
@Validated
public class PluginController {

	@Autowired
	PluginService pluginService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	void createPlugin(
		@RequestBody @Valid Plugin plugin
	) {
		pluginService.create(plugin);
	}

	@GetMapping
	HttpEntity<Plugin> getPlugin(
		WebRequest request,
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = Plugin.REGEX) String tag
	) {
		return ifModifiedSince(request, pluginService.get(tag));
	}

	@GetMapping("exists")
	boolean pluginExists(
		@RequestParam String tag
	) {
		return pluginService.exists(tag);
	}

	@GetMapping("list")
	HttpEntity<List<Plugin>> getList(
		WebRequest request,
		@RequestParam @Size(max = 100) List<@Length(max = QTAG_LEN) @Pattern(regexp = Plugin.REGEX) String> tags
	) {
		return ifModifiedSinceList(request, tags.stream().map(tag -> {
			try {
				return pluginService.get(tag);
			} catch (NotFoundException | AccessDeniedException e) {
				return null;
			}
		}).toList());
	}

	@GetMapping("page")
	Page<Plugin> getPage(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Length(max = QUERY_LEN) @Pattern(regexp = TagFilter.QUERY) String query,
		@RequestParam(required = false) Instant modifiedAfter
	) {
		return pluginService.page(
			TagFilter
				.builder()
				.modifiedAfter(modifiedAfter)
				.query(query).build(),
			pageable);
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updatePlugin(
		@RequestBody @Valid Plugin plugin
	) {
		pluginService.update(plugin);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deletePlugin(
		@RequestParam @Length(max = QTAG_LEN) @Pattern(regexp = Plugin.REGEX) String tag
	) {
		pluginService.delete(tag);
	}
}
