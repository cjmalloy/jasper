package ca.hc.jasper.service.dto;

import java.time.Instant;
import java.util.List;

import ca.hc.jasper.domain.proj.HasTags;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefDto implements HasTags {
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

	@JsonIgnore
	public boolean local() {
		return origin == null || origin.isBlank();
	}
}
