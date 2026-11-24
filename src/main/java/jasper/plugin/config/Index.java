package jasper.plugin.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Jacksonized @Builder
@JsonInclude(Include.NON_NULL)
public class Index implements Serializable {
	@Builder.Default
	private boolean tags = true;
	@Builder.Default
	private boolean sources = true;
	@Builder.Default
	private boolean alts = true;
	@Builder.Default
	private boolean responses = true;
	@Builder.Default
	private boolean internalResponses = true;
	@Builder.Default
	private boolean fulltext = true;
	@Builder.Default
	private boolean published = true;
	@Builder.Default
	private boolean modified = true;
	@Builder.Default
	private boolean created = true;
	@Builder.Default
	private boolean title = true;
	@Builder.Default
	private boolean origin = true;
	@Builder.Default
	private boolean newResponse = true;
	@Builder.Default
	private boolean newReaction = true;
	/**
	 * Tag value sorts to index, e.g. "plugin/progress:num" or "tags->plugin/duration:dur".
	 */
	@Builder.Default
	private List<String> hotTags = new ArrayList<>();
	/**
	 * Plugin sort columns to index, e.g. "plugin/test->size:num" or "plugins->plugin/test->name".
	 */
	@Builder.Default
	private List<String> hotPlugins = new ArrayList<>();
	/**
	 * Plugins to index local response counts for, e.g. "plugin/comment" for
	 * sorting and filtering by "metadata->plugins->plugin/comment".
	 */
	@Builder.Default
	private List<String> hotResponses = new ArrayList<>();
	/**
	 * Plugins to index response counts from all origins for, e.g. "plugin/comment" for
	 * sorting and filtering by "metadata->remotePlugins->plugin/comment".
	 */
	@Builder.Default
	private List<String> hotRemoteResponses = new ArrayList<>();
	@Builder.Default
	private boolean ignored = true;
	@Builder.Default
	private boolean cascade = true;
	@Builder.Default
	private boolean regen = true;
}
