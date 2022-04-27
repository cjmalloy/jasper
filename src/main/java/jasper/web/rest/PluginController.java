package jasper.web.rest;

import java.time.Instant;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import jasper.domain.Plugin;
import jasper.repository.filter.TagFilter;
import jasper.service.PluginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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
	Plugin getPlugin(
		@RequestParam @Pattern(regexp = Plugin.REGEX) String tag
	) {
		return pluginService.get(tag);
	}

	@GetMapping("exists")
	boolean pluginExists(
		@RequestParam String tag
	) {
		return pluginService.exists(tag);
	}

	@GetMapping("page")
	Page<Plugin> getPage(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagFilter.QUERY) String query,
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
		@RequestParam @Pattern(regexp = Plugin.REGEX) String tag
	) {
		pluginService.delete(tag);
	}
}
