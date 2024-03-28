package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.proj.HasTags;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RefNodeDto implements HasTags, Serializable {
	private String url;
	private String origin;
	private String title;
	private String comment;
	private List<String> tags;
	private List<String> sources;
	private List<String> responses;
	private List<String> alternateUrls;
	private ObjectNode plugins;
	private MetadataDto metadata;
	private Instant published;
	private Instant created;
	private Instant modified;
}
