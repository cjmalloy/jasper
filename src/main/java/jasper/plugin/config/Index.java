package jasper.plugin.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
public record Index(
	boolean tags,
	boolean sources,
	boolean alts,
	boolean fulltext,
	boolean published,
	boolean modified
) implements Serializable {
	
	// Default constructor with all true values
	public Index() {
		this(true, true, true, true, true, true);
	}
}
