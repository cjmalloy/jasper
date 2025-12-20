package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.proj.HasTags;
import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@Builder(toBuilder = true)
@JsonDeserialize(builder = RefDto.RefDtoBuilder.class)
@JsonInclude(NON_EMPTY)
public record RefDto(
	String url,
	String origin,
	String title,
	String comment,
	List<String> tags,
	List<String> sources,
	List<String> alternateUrls,
	ObjectNode plugins,
	MetadataDto metadata,
	Instant published,
	Instant created,
	Instant modified
) implements HasTags, Serializable {
	
	@JsonPOJOBuilder(withPrefix = "")
	public static class RefDtoBuilder {
		// Lombok will generate this class
		// Initialize default
		private String origin = "";
	}
}
