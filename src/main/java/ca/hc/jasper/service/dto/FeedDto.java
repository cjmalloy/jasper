package ca.hc.jasper.service.dto;

import java.time.Instant;
import java.util.List;

import ca.hc.jasper.domain.proj.HasTags;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeedDto implements HasTags {
	private String origin;
	private String name;
	private String proxy;
	private List<String> tags;
	private Instant modified;
	private Instant lastScrape;

	@JsonIgnore
	public boolean local() {
		return origin == null || origin.isBlank();
	}
}
