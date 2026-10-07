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

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static jasper.domain.proj.Tag.matchesTemplate;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(NON_EMPTY)
public class Metadata implements Serializable {

	@Builder.Default
	private String modified = Instant.now().toString();
	private List<String> expandedTags;
	private List<String> responses;
	private List<String> internalResponses;
	private Map<String, Long> plugins;
	/**
	 * Plugin response counts from other origins than this Ref.
	 * Null if the metadata was generated before remote counts were tracked.
	 */
	@JsonInclude(NON_NULL)
	private Map<String, Long> remotePlugins;
	private Map<String, List<String>> userUrls;
	@JsonInclude(NON_DEFAULT)
	private boolean obsolete = false;
	@JsonInclude(NON_DEFAULT)
	private boolean regen = false;
	@JsonInclude(NON_DEFAULT)
	private boolean cascade = false;

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

	public void addPlugins(List<String> add, String url, boolean local) {
		initRemotePlugins();
		if (userUrls == null) userUrls = new HashMap<>();
		for (var plugin : add) {
			plugins.merge(plugin, 1L, Long::sum);
			if (!local && remotePlugins != null) remotePlugins.merge(plugin, 1L, Long::sum);
			if (matchesTemplate("plugin/user", plugin)) {
				if (userUrls.containsKey(plugin)) {
					var list = userUrls.get(plugin);
					if (!list.contains(url)) list.add(url);
				} else {
					userUrls.put(plugin, new ArrayList<>(List.of(url)));
				}
			}
		}
		modified = Instant.now().toString();
	}

	public void removePlugins(List<String> remove, String url, boolean local) {
		initRemotePlugins();
		if (userUrls == null) userUrls = new HashMap<>();
		var changed = false;
		for (var plugin : remove) {
			if (decrement(plugins, plugin)) changed = true;
			if (!local && remotePlugins != null && decrement(remotePlugins, plugin)) changed = true;
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
			}
		}
		if (changed) modified = Instant.now().toString();
	}

	private void initRemotePlugins() {
		if (plugins == null) plugins = new HashMap<>();
		if (remotePlugins != null) return;
		if (plugins.isEmpty()) {
			remotePlugins = new HashMap<>();
		} else {
			// Metadata generated before remote counts were tracked
			regen = true;
		}
	}

	private static boolean decrement(Map<String, Long> counts, String plugin) {
		if (!counts.containsKey(plugin)) return false;
		var count = counts.get(plugin) - 1;
		if (count > 0) {
			counts.put(plugin, count);
		} else {
			counts.remove(plugin);
		}
		return true;
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
