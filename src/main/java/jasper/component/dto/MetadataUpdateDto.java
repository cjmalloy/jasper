package jasper.component.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@Builder(toBuilder = true)
@JsonInclude(NON_EMPTY)
public record MetadataUpdateDto(
	String modified,
	int responses,
	int internalResponses,
	Map<String, Integer> plugins,
	boolean obsolete
) implements Serializable {
	
	public MetadataUpdateDto {
		// Set default for modified if null
		if (modified == null) modified = Instant.now().toString();
	}
}
