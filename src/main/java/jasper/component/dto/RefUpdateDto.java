package jasper.component.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.proj.HasTags;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * DTO for sending a Ref update to a subscribed client. This DTO does not
 * include metadata.
 *
 * We can't verify what tags the user has access to, so all private tags
 * and plugins are dropped.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RefUpdateDto implements HasTags {
	private String url;
	private String origin;
	private String title;
	private String comment;
	private List<String> tags;
	private List<String> sources;
	private List<String> alternateUrls;
	private ObjectNode plugins;
	private Instant published;
	private Instant created;
	private Instant modified;
}
