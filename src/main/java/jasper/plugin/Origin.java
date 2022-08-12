package jasper.domain.plugin;

import jasper.domain.Ext;
import jasper.domain.Plugin;
import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.User;
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
	private List<String> removeTags;
	private Map<String, String> mapTags;
	private List<String> addTags;

	public void migrate(Ref ref) {
		if (mapTags != null) {

		}
	}

	public void migrate(Ext ext) {
		if (mapTags != null) {

		}
	}

	public void migrate(Plugin plugin) {
		if (mapTags != null) {

		}
	}

	public void migrate(Template template) {
		if (mapTags != null) {

		}
	}

	public void migrate(User user) {
		if (mapTags != null) {

		}
	}
}
