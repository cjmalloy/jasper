package jasper.service.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jasper.domain.proj.HasTags;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class FeedDto implements HasTags {
	private String url;
	private String origin;
	private String name;
	private List<String> tags;
	private Instant modified;
	private Instant lastScrape;
	private Duration scrapeInterval;
	private boolean scrapeDescription;
	private boolean removeDescriptionIndent;
}
