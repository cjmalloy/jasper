package jasper.domain.plugin;

import jasper.domain.Ref;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class Feed {

	private List<String> addTags;

	private Instant lastScrape;

	private Duration scrapeInterval;

	private boolean scrapeDescription = true;

	private boolean removeDescriptionIndent = false;
}
