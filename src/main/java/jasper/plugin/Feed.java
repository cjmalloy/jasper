package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Feed {
	private String origin;
	private List<String> addTags;
	private Instant lastScrape;
	private boolean disableEtag;
	private String etag;
	private Duration scrapeInterval;
	private boolean scrapeDescription;
	private boolean scrapeContents;
	private boolean scrapeAuthor;
}
