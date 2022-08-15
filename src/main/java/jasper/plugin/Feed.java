package jasper.plugin;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class Feed {
	private String origin;
	private List<String> addTags;
	private Instant lastScrape;
	private Duration scrapeInterval;
	private boolean scrapeDescription = true;
	private boolean removeDescriptionIndent = false;
}
