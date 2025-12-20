package jasper.component.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.proj.HasTags;
import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

/**
 * DTO for sending a Ref update to a subscribed client.
 * <p>
 * We can't verify what tags the user has access to, so all private tags
 * metadata, and plugins are dropped.
 */
@Builder(toBuilder = true)
@JsonInclude(NON_EMPTY)
public record RefUpdateDto(
	String url,
	String origin,
	String title,
	String comment,
	List<String> tags,
	List<String> sources,
	List<String> alternateUrls,
	ObjectNode plugins,
	MetadataUpdateDto metadata,
	Instant published,
	Instant created,
	Instant modified
) implements HasTags, Serializable {
}
