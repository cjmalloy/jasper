package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Config implements Serializable {
	private String emailHost = "jasper.local";
	private int maxSources = 1000;
	private int ingestMaxRetry = 5;
	private List<String> modSeals = List.of("seal", "+seal", "_seal", "_moderated");
	private List<String> editorSeals = List.of("plugin/qc");
	/**
	 * Tags for config Refs to be cached.
	 */
	private List<String> cacheTags = List.of("_config", "+plugin/scrape", "+plugin/oembed");
	/**
	 * Whitelist origins to run async tasks on.
	 */
	private List<String> asyncOrigins = List.of("");
	/**
	 * Whitelist origins to be allowed to scrape using +plugin/feed.
	 */
	private List<String> scrapeOrigins = List.of("");
	/**
	 * Whitelist domains to be allowed to scrape.
	 */
	private List<String> scrapeHostWhitelist = null;
	/**
	 * Blacklist domains to be allowed to scrape. Takes precedence over domain whitelist.
	 */
	private List<String> scrapeHostBlacklist = List.of("*.local");
	/**
	 * Whitelist origins to be allowed to replicate using +plugin/origin.
	 */
	private List<String> replicateOrigins = List.of("");
	private int maxReplicateBatch = 5000;
	/**
	 * Whitelist origins to be allowed to open SSH tunnels.
	 */
	private List<String> sshOrigins = List.of("");
}
