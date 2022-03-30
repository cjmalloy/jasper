package ca.hc.jasper.service.dto;

import java.time.Instant;
import java.util.List;

import ca.hc.jasper.domain.proj.HasTags;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeedDto implements HasTags {
	private String url;
	private String origin;
	private String name;
	private List<String> tags;
	private Instant modified;
	private Instant lastScrape;
}
