package jasper.service.dto;

import java.time.Instant;
import java.util.List;

import jasper.domain.Metadata;
import jasper.domain.proj.HasTags;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class RefDto implements HasTags {
	private String url;
	private String origin;
	private String title;
	private String comment;
	private List<String> tags;
	private List<String> sources;
	private List<String> alternateUrls;
	private ObjectNode plugins;
	private Metadata metadata;
	private Instant published;
	private Instant created;
	private Instant modified;
}
