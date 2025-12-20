package jasper.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static jasper.domain.proj.Tag.matchesTemplate;

@JsonInclude(NON_EMPTY)
public record Metadata(
	String modified,
	List<String> expandedTags,
	List<String> responses,
	List<String> internalResponses,
	Map<String, Long> plugins,
	Map<String, List<String>> userUrls,
	@JsonInclude(NON_DEFAULT) boolean obsolete,
	@JsonInclude(NON_DEFAULT) boolean regen
) implements Serializable {

	// Default constructor with current timestamp
	public Metadata {
		if (modified == null) {
			modified = Instant.now().toString();
		}
	}

	// Helper method to create a copy with modified fields
	public Metadata withModified(String modified) {
		return new Metadata(modified, expandedTags, responses, internalResponses, plugins, userUrls, obsolete, regen);
	}

	public Metadata withObsolete(boolean obsolete) {
		return new Metadata(modified, expandedTags, responses, internalResponses, plugins, userUrls, obsolete, regen);
	}

	public Metadata withAddedResponse(String url) {
		var newResponses = responses == null ? new ArrayList<String>() : new ArrayList<>(responses);
		var newInternalResponses = internalResponses == null ? null : new ArrayList<>(internalResponses);
		var newModified = modified;
		
		if (!newResponses.contains(url)) {
			newModified = Instant.now().toString();
			newResponses.add(url);
		}
		if (newInternalResponses != null && newInternalResponses.contains(url)) {
			newModified = Instant.now().toString();
			newInternalResponses.remove(url);
		}
		
		return new Metadata(
			newModified,
			expandedTags,
			newResponses.isEmpty() ? null : newResponses,
			newInternalResponses != null && newInternalResponses.isEmpty() ? null : newInternalResponses,
			plugins,
			userUrls,
			obsolete,
			regen
		);
	}

	public Metadata withAddedInternalResponse(String url) {
		var newResponses = responses == null ? null : new ArrayList<>(responses);
		var newInternalResponses = internalResponses == null ? new ArrayList<String>() : new ArrayList<>(internalResponses);
		var newModified = modified;
		
		if (!newInternalResponses.contains(url)) {
			newModified = Instant.now().toString();
			newInternalResponses.add(url);
		}
		if (newResponses != null && newResponses.contains(url)) {
			newModified = Instant.now().toString();
			newResponses.remove(url);
		}
		
		return new Metadata(
			newModified,
			expandedTags,
			newResponses != null && newResponses.isEmpty() ? null : newResponses,
			newInternalResponses.isEmpty() ? null : newInternalResponses,
			plugins,
			userUrls,
			obsolete,
			regen
		);
	}

	public Metadata withAddedPlugins(List<String> add, String url) {
		var newPlugins = plugins == null ? new HashMap<String, Long>() : new HashMap<>(plugins);
		var newUserUrls = userUrls == null ? new HashMap<String, List<String>>() : new HashMap<>(userUrls);
		
		for (var plugin : add) {
			if (newPlugins.containsKey(plugin)) {
				newPlugins.put(plugin, newPlugins.get(plugin) + 1);
			} else {
				newPlugins.put(plugin, 1L);
			}
			if (matchesTemplate("plugin/user", plugin)) {
				if (newUserUrls.containsKey(plugin)) {
					var list = new ArrayList<>(newUserUrls.get(plugin));
					if (!list.contains(url)) list.add(url);
					newUserUrls.put(plugin, list);
				} else {
					newUserUrls.put(plugin, new ArrayList<>(List.of(url)));
				}
			}
		}
		
		return new Metadata(
			Instant.now().toString(),
			expandedTags,
			responses,
			internalResponses,
			newPlugins.isEmpty() ? null : newPlugins,
			newUserUrls.isEmpty() ? null : newUserUrls,
			obsolete,
			regen
		);
	}

	public Metadata withRemovedPlugins(List<String> remove, String url) {
		var newPlugins = plugins == null ? new HashMap<String, Long>() : new HashMap<>(plugins);
		var newUserUrls = userUrls == null ? new HashMap<String, List<String>>() : new HashMap<>(userUrls);
		var changed = false;
		
		for (var plugin : remove) {
			if (newPlugins.containsKey(plugin)) {
				changed = true;
				var count = newPlugins.get(plugin) - 1;
				if (count > 0) {
					newPlugins.put(plugin, count);
				} else {
					newPlugins.remove(plugin);
				}
			}
			if (matchesTemplate("plugin/user", plugin)) {
				for (var entry : new HashMap<>(newUserUrls).entrySet()) {
					var list = new ArrayList<>(entry.getValue());
					if (list.contains(url)) {
						changed = true;
						list.remove(url);
						if (list.isEmpty()) {
							newUserUrls.remove(entry.getKey());
						} else {
							newUserUrls.put(entry.getKey(), list);
						}
					}
				}
			}
		}
		
		if (!changed) return this;
		
		return new Metadata(
			Instant.now().toString(),
			expandedTags,
			responses,
			internalResponses,
			newPlugins.isEmpty() ? null : newPlugins,
			newUserUrls.isEmpty() ? null : newUserUrls,
			obsolete,
			regen
		);
	}

	public Metadata withRemoved(String url) {
		var newResponses = responses == null ? null : new ArrayList<>(responses);
		var newInternalResponses = internalResponses == null ? null : new ArrayList<>(internalResponses);
		var changed = false;
		
		if (newResponses != null && newResponses.contains(url)) {
			changed = true;
			newResponses.remove(url);
		}
		if (newInternalResponses != null && newInternalResponses.contains(url)) {
			changed = true;
			newInternalResponses.remove(url);
		}
		
		if (!changed) return this;
		
		return new Metadata(
			Instant.now().toString(),
			expandedTags,
			newResponses != null && newResponses.isEmpty() ? null : newResponses,
			newInternalResponses != null && newInternalResponses.isEmpty() ? null : newInternalResponses,
			plugins,
			userUrls,
			obsolete,
			regen
		);
	}
}
