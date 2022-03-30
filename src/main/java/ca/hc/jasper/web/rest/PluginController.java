package ca.hc.jasper.web.rest;

import java.time.Instant;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.Plugin;
import ca.hc.jasper.domain.Tag;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.repository.filter.TagList;
import ca.hc.jasper.service.PluginService;
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
		@RequestParam @Pattern(regexp = Tag.REGEX) String tag,
		@RequestParam(defaultValue = "") String origin
	) {
		return pluginService.get(tag, origin);
	}

	@GetMapping("list")
	Page<Plugin> getPlugins(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagList.REGEX) String query,
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
		@RequestParam @Pattern(regexp = Tag.REGEX) String tag
	) {
		pluginService.delete(tag);
	}
}
