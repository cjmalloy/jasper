package jasper.plugin.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;

import java.io.Serializable;

@Builder
@JsonDeserialize(builder = Index.IndexBuilder.class)
@JsonInclude(Include.NON_NULL)
public record Index(
	boolean tags,
	boolean sources,
	boolean alts,
	boolean fulltext,
	boolean published,
	boolean modified
) implements Serializable {
	
	@JsonPOJOBuilder(withPrefix = "")
	public static class IndexBuilder {
		// Lombok will generate this class
		// Initialize defaults
		private boolean tags = true;
		private boolean sources = true;
		private boolean alts = true;
		private boolean fulltext = true;
		private boolean published = true;
		private boolean modified = true;
	}
}
