package jasper.component.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@Builder(toBuilder = true)
@JsonDeserialize(builder = MetadataUpdateDto.MetadataUpdateDtoBuilder.class)
@JsonInclude(NON_EMPTY)
public record MetadataUpdateDto(
	String modified,
	int responses,
	int internalResponses,
	Map<String, Integer> plugins,
	boolean obsolete
) implements Serializable {
	
	@JsonPOJOBuilder(withPrefix = "")
	public static class MetadataUpdateDtoBuilder {
		// Lombok will generate this class
		// Initialize default
		private String modified = Instant.now().toString();
	}
}
