package jasper.domain.plugin;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;


@Getter
@Setter
public class Origin {
	private String query;
	private String proxy;
	private Instant lastScrape;
	private Duration scrapeInterval;
	private Map<String, String> mapTags;
	private List<String> addTags;
}
