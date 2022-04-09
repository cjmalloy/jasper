package ca.hc.jasper.domain;

import java.util.List;
import java.util.Map;

import lombok.*;

@Getter
@Setter
@Builder
public class Metadata {
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
			responses.add(url);
		}
	}

	public void addInternalResponse(String url) {
		if (!internalResponses.contains(url)) {
			internalResponses.add(url);
		}
	}

	public void addPlugins(Map<String, String> add) {
		for (var e : add.entrySet()) {
			var k = e.getKey();
			var v = e.getValue();
			if (plugins.containsKey(k)) {
				var list = plugins.get(k);
				if (!list.contains(v)) {
					list.add(v);
				}
			} else {
				plugins.put(k, List.of(v));
			}
		}
	}

	public void remove(String url) {
		responses.remove(url);
		internalResponses.remove(url);
		for (var list : plugins.values()) {
			list.remove(url);
		}
	}
}
