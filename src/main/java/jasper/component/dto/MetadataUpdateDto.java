package jasper.component.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@JsonInclude(NON_EMPTY)
public record MetadataUpdateDto(
	String modified,
	int responses,
	int internalResponses,
	Map<String, Integer> plugins,
	boolean obsolete
) implements Serializable {
	
	// Default constructor with current timestamp
	public MetadataUpdateDto {
		if (modified == null) {
			modified = Instant.now().toString();
		}
	}
	
	// Helper method for MapStruct
	public MetadataUpdateDto withPlugins(Map<String, Integer> plugins) {
		return new MetadataUpdateDto(modified, responses, internalResponses, plugins, obsolete);
	}
}
