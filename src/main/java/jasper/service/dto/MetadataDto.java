package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@Builder(toBuilder = true)
@JsonDeserialize(builder = MetadataDto.MetadataDtoBuilder.class)
@JsonInclude(NON_EMPTY)
public record MetadataDto(
	String modified,
	int responses,
	int internalResponses,
	Map<String, Integer> plugins,
	List<String> userUrls,
	boolean obsolete
) implements Serializable {
	
	@JsonPOJOBuilder(withPrefix = "")
	public static class MetadataDtoBuilder {
		// Lombok will generate this class
		// Initialize default
		private String modified = Instant.now().toString();
	}
}
