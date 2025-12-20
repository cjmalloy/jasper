package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@JsonInclude(NON_EMPTY)
public record MetadataDto(
	String modified,
	int responses,
	int internalResponses,
	Map<String, Integer> plugins,
	List<String> userUrls,
	boolean obsolete
) implements Serializable {
	
	// Default constructor with current timestamp
	public MetadataDto {
		if (modified == null) {
			modified = Instant.now().toString();
		}
	}
	
	// Helper methods for MapStruct
	public MetadataDto withPlugins(Map<String, Integer> plugins) {
		return new MetadataDto(modified, responses, internalResponses, plugins, userUrls, obsolete);
	}
	
	public MetadataDto withUserUrls(List<String> userUrls) {
		return new MetadataDto(modified, responses, internalResponses, plugins, userUrls, obsolete);
	}
}
