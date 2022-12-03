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
	private Instant modified = Instant.now();
	private List<String> responses;
	private List<String> internalResponses;
	private Map<String, List<String>> plugins;

	// TODO: remote stats
//	private List<String> origins;
//	private List<String> sourcesAcrossOrigins;
//	private List<String> responsesAcrossOrigins;
//	private Map<String, String> pluginsAcrossOrigins;

	// TODO: recursive stats
//	private Long recursiveResponseCount;
//	private Map<String, Long> recursivePluginCount;
//	private Instant lastRecurse;

	public void addResponse(String url) {
		if (responses == null) {
			responses = new ArrayList<>();
		}
		if (!responses.contains(url)) {
			modified = Instant.now();
			responses.add(url);
		}
	}

	public void addInternalResponse(String url) {
		if (internalResponses == null) {
			internalResponses = new ArrayList<>();
		}
		if (!internalResponses.contains(url)) {
			modified = Instant.now();
			internalResponses.add(url);
		}
	}

	public void addPlugins(Map<String, String> add) {
		if (plugins == null) {
			plugins = new HashMap<>();
		}
		var changed = false;
		for (var e : add.entrySet()) {
			var k = e.getKey();
			var v = e.getValue();
			if (plugins.containsKey(k)) {
				var list = plugins.get(k);
				if (!list.contains(v)) {
					changed = true;
					list.add(v);
				}
			} else {
				changed = true;
				plugins.put(k, List.of(v));
			}
		}
		if (changed) modified = Instant.now();
	}

	public void remove(String url) {
		modified = Instant.now();
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
