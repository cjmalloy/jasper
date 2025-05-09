package jasper.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jasper.domain.proj.Tag.matchesTemplate;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Metadata implements Serializable {

	@Builder.Default
	private String modified = Instant.now().toString();
	private List<String> responses;
	private List<String> internalResponses;
	private Map<String, Long> pluginCounts;
	private Map<String, List<String>> userUrls;
	private boolean obsolete = false;

	public void addResponse(String url) {
		if (responses == null) {
			responses = new ArrayList<>();
		}
		if (!responses.contains(url)) {
			modified = Instant.now().toString();
			responses.add(url);
		}
		if (internalResponses != null && responses.contains(url)) {
			modified = Instant.now().toString();
			internalResponses.remove(url);
		}
	}

	public void addInternalResponse(String url) {
		if (internalResponses == null) {
			internalResponses = new ArrayList<>();
		}
		if (!internalResponses.contains(url)) {
			modified = Instant.now().toString();
			internalResponses.add(url);
		}
		if (responses != null && responses.contains(url)) {
			modified = Instant.now().toString();
			responses.remove(url);
		}
	}

	public void addPlugins(List<String> add, String url) {
		if (pluginCounts == null) pluginCounts = new HashMap<>();
		if (userUrls == null) userUrls = new HashMap<>();
		var changed = false;
		for (var plugin : add) {
			if (matchesTemplate("plugin/user", plugin)) {
				if (userUrls.containsKey(plugin)) {
					var list = userUrls.get(plugin);
					if (!list.contains(url)) {
						changed = true;
						list.add(url);
					}
				} else {
					changed = true;
					userUrls.put(plugin, List.of(url));
				}
			} else {
				changed = true;
				if (pluginCounts.containsKey(plugin)) {
					pluginCounts.put(plugin, pluginCounts.get(plugin) + 1);
				} else {
					pluginCounts.put(plugin, 1L);
				}
			}
		}
		if (changed) modified = Instant.now().toString();
	}

	public void removePlugins(List<String> remove, String url) {
		if (pluginCounts == null) pluginCounts = new HashMap<>();
		if (userUrls == null) userUrls = new HashMap<>();
		var changed = false;
		for (var plugin : remove) {
			if (matchesTemplate("plugin/user", plugin)) {
				for (var entry : userUrls.entrySet()) {
					var list = entry.getValue();
					if (list.contains(url)) {
						changed = true;
						try {
							list.remove(url);
						} catch (UnsupportedOperationException e) {
							userUrls.put(entry.getKey(), list = new ArrayList<>(list));
							list.remove(url);
						}
					}
				}
			} else if (pluginCounts.containsKey(plugin)) {
				changed = true;
				var count = pluginCounts.get(plugin) - 1;
				if (count > 0) {
					pluginCounts.put(plugin, pluginCounts.get(plugin) - 1);
				} else {
					pluginCounts.remove(plugin);
				}
			}
		}
		if (changed) modified = Instant.now().toString();
	}

	public void remove(String url) {
		if (responses != null && responses.contains(url)) {
			modified = Instant.now().toString();
			responses.remove(url);
		}
		if (internalResponses != null && internalResponses.contains(url)) {
			modified = Instant.now().toString();
			internalResponses.remove(url);
		}
	}
}
