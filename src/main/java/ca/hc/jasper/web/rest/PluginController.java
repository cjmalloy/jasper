package ca.hc.jasper.web.rest;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.Plugin;
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
		@Valid @RequestBody Plugin plugin
	) {
		pluginService.create(plugin);
	}

	@GetMapping
	Plugin getPlugin(
		@RequestParam String tag
	) {
		return pluginService.get(tag);
	}

	@GetMapping("list")
	Page<Plugin> getPlugins(
		@PageableDefault(sort = "tag") Pageable pageable,
		@RequestParam(required = false) @Pattern(regexp = TagList.REGEX) String query
	) {
		return pluginService.page(
			TagFilter.builder()
				.query(query).build(),
			pageable);
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updatePlugin(
		@Valid @RequestBody Plugin plugin
	) {
		pluginService.update(plugin);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deletePlugin(
		@RequestParam String tag
	) {
		pluginService.delete(tag);
	}
}
