package jasper.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;
import static jasper.domain.proj.Tag.matchesTemplate;
import static java.time.ZoneOffset.UTC;

@Getter
@Setter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(NON_EMPTY)
public class Metadata implements Serializable {

	/**
	 * Fixed-width timestamp format so lexical order matches chronological order when sorting.
	 */
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'").withZone(UTC);

	@Builder.Default
	private String modified = Instant.now().toString();
	/**
	 * Last time a new response was created, ignoring user urls.
	 */
	@Builder.Default
	private String newResponse = timestamp(Instant.now());
	/**
	 * Last time any response was created or updated, including user urls.
	 */
	@Builder.Default
	private String newReaction = timestamp(Instant.now());
	private List<String> expandedTags;
	private List<String> responses;
	private List<String> internalResponses;
	private Map<String, Long> plugins;
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

	public void addPlugins(List<String> add, String userUrl, boolean local) {
		initPlugins();
		for (var plugin : add) {
			if (local) plugins.merge(plugin, 1L, Long::sum);
			remotePlugins.merge(plugin, 1L, Long::sum);
			if (userUrl != null && matchesTemplate("plugin/user", plugin)) {
				var list = userUrls.computeIfAbsent(plugin, k -> new ArrayList<>());
				if (!list.contains(userUrl)) list.add(userUrl);
			}
		}
		modified = Instant.now().toString();
	}

	public void removePlugins(List<String> remove, String userUrl, boolean local) {
		initPlugins();
		var changed = false;
		for (var plugin : remove) {
			if (local && decrement(plugins, plugin)) changed = true;
			if (decrement(remotePlugins, plugin)) changed = true;
			if (userUrl != null && matchesTemplate("plugin/user", plugin)) {
				for (var entry : userUrls.entrySet()) {
					var list = entry.getValue();
					if (list.contains(userUrl)) {
						changed = true;
						try {
							list.remove(userUrl);
						} catch (UnsupportedOperationException e) {
							userUrls.put(entry.getKey(), list = new ArrayList<>(list));
							list.remove(userUrl);
						}
					}
				}
			}
		}
		if (changed) modified = Instant.now().toString();
	}

	private void initPlugins() {
		if (plugins == null) plugins = new HashMap<>();
		if (remotePlugins == null) remotePlugins = new HashMap<>();
		if (userUrls == null) userUrls = new HashMap<>();
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

	/**
	 * Format an instant as a fixed-width timestamp for newResponse and newReaction.
	 */
	public static String timestamp(Instant instant) {
		return TIMESTAMP.format(instant);
	}
}
