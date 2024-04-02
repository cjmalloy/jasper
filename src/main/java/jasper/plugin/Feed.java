package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Feed implements Serializable {
	private List<String> addTags;
	private Instant lastScrape;
	private boolean disableEtag;
	private String etag;
	private Duration scrapeInterval;
	private boolean stripQuery;
	private boolean scrapeWebpage;
	private boolean scrapeDescription;
	private boolean scrapeContents;
	private boolean scrapeAuthors;
	private boolean scrapeThumbnail;
	private boolean scrapeAudio;
	private boolean scrapeVideo;
	private boolean scrapeEmbed;
}
