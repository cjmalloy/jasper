package jasper.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Metadata {

	@Builder.Default
	private String modified = Instant.now().toString();
	private List<String> responses;
	private List<String> internalResponses;
	private Map<String, List<String>> plugins;
	private boolean obsolete = false;

	public void addResponse(String url) {
		if (responses == null) {
			responses = new ArrayList<>();
		}
		if (!responses.contains(url)) {
			modified = Instant.now().toString();
			responses.add(url);
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
	}

	public void addPlugins(List<String> add, String url) {
		if (plugins == null) {
			plugins = new HashMap<>();
		}
		var changed = false;
		for (var plugin : add) {
			if (plugins.containsKey(plugin)) {
				var list = plugins.get(plugin);
				if (!list.contains(url)) {
					changed = true;
					list.add(url);
				}
			} else {
				changed = true;
				plugins.put(plugin, List.of(url));
			}
		}
		if (changed) modified = Instant.now().toString();
	}

	public void remove(String url) {
		modified = Instant.now().toString();
		if (responses != null) responses.remove(url);
		if (internalResponses != null) internalResponses.remove(url);
		if (plugins != null) {
			for (var entry : plugins.entrySet()) {
				var list = entry.getValue();
				try {
					list.remove(url);
				} catch (UnsupportedOperationException e) {
					plugins.put(entry.getKey(), list = new ArrayList<>(list));
					list.remove(url);
				}
			}
		}
	}
}
