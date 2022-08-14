package jasper.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Metadata {

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
		if (!responses.contains(url)) {
			modified = Instant.now();
			responses.add(url);
		}
	}

	public void addInternalResponse(String url) {
		if (!internalResponses.contains(url)) {
			modified = Instant.now();
			internalResponses.add(url);
		}
	}

	public void addPlugins(Map<String, String> add) {
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
		responses.remove(url);
		internalResponses.remove(url);
		for (var list : plugins.values()) {
			list.remove(url);
		}
	}
}
