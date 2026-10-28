package simulations.jasper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Deterministic generator for a knowledge base shaped like real usage:
 * a long tail of topics and authors (Zipf), mostly links with some
 * threads of comments concentrated on popular Refs, publish dates skewed
 * to recent.
 */
public final class Dataset {
	private Dataset() {}

	public static final List<String> TOPICS = List.of(
		"news", "tech", "science", "programming", "music", "politics", "news/world", "tech/ai", "science/space",
		"programming/python", "history", "books", "film", "tech/linux", "economics", "health", "art", "tech/security",
		"science/physics", "programming/rust", "philosophy", "math", "sports", "cooking", "travel", "design",
		"news/local", "music/jazz", "science/biology", "gaming", "photography", "climate", "education", "law",
		"psychology", "languages", "architecture", "diy", "tech/java", "tech/javascript");

	public static final List<String> WORDS = List.of(
		"analysis", "archive", "battery", "budget", "climate", "compiler", "concert", "database", "debate", "design",
		"economy", "election", "energy", "experiment", "festival", "framework", "galaxy", "garden", "genome", "guide",
		"history", "interview", "journal", "kernel", "language", "lecture", "library", "market", "memory", "museum",
		"network", "novel", "ocean", "orbit", "painting", "planet", "policy", "protocol", "quantum", "recipe",
		"release", "research", "review", "satellite", "security", "server", "startup", "storage", "study", "survey",
		"telescope", "theory", "tutorial", "vaccine", "village", "virus", "volcano", "weather", "workshop", "zoning");

	public static final double LINK_FRACTION = 0.7;

	public static String userName(String role, int i) {
		return role.toLowerCase() + i;
	}

	/** Zipf distributed index in [0, n). */
	public static int zipf(Random random, int n, double s) {
		double total = 0;
		for (int i = 1; i <= n; i++) total += 1 / Math.pow(i, s);
		double r = random.nextDouble() * total;
		for (int i = 1; i <= n; i++) {
			r -= 1 / Math.pow(i, s);
			if (r <= 0) return i - 1;
		}
		return n - 1;
	}

	public static int zipf(int n) {
		return zipf(ThreadLocalRandom.current(), n, 1.1);
	}

	public static String topic(Random random, List<String> topics) {
		return topics.get(zipf(random, topics.size(), 1.1));
	}

	public static String words(Random random, int min, int max) {
		var count = min + random.nextInt(max - min + 1);
		var sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			if (i > 0) sb.append(' ');
			sb.append(WORDS.get(zipf(random, WORDS.size(), 0.8)));
		}
		return sb.toString();
	}

	public static String title(Random random) {
		var w = words(random, 3, 8);
		return Character.toUpperCase(w.charAt(0)) + w.substring(1);
	}

	public static String text(Random random, int maxSentences) {
		var sentences = random.nextInt(maxSentences + 1);
		var sb = new StringBuilder();
		for (int i = 0; i < sentences; i++) {
			if (i > 0) sb.append(' ');
			sb.append(title(random)).append('.');
		}
		return sb.toString();
	}

	public static String linkUrl(int i, String topic) {
		if (!LoadConfig.UPSTREAM.isEmpty() && i % 20 == 0) return LoadConfig.UPSTREAM + "/image.png?n=" + i;
		return "https://loadtest.example.com/" + topic + "/" + i;
	}

	public static String commentUrl(int i) {
		return "comment:loadtest-" + i;
	}

	/**
	 * JSON array of Refs [from, to) for replication push.
	 */
	public static String refs(int from, int to, int total, List<String> topics) {
		var links = (int) (total * LINK_FRACTION);
		var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		var modified = uniqueModified(to - from);
		var json = new StringBuilder("[");
		for (int i = from; i < to; i++) {
			var random = new Random(i * 7919L);
			if (i > from) json.append(',');
			var author = "+user/" + userName("USER", zipf(random, LoadConfig.USERS, 1.0));
			var tags = new LinkedHashSet<String>();
			if (random.nextDouble() < 0.97) tags.add("public");
			tags.add(author);
			// Skew publish dates to recent: most activity in the last few weeks
			var age = Duration.ofMinutes((long) (Math.pow(random.nextDouble(), 3) * 365 * 24 * 60));
			var sources = new ArrayList<String>();
			String url;
			String title = "";
			if (i < links) {
				var first = topic(random, topics);
				url = linkUrl(i, first);
				title = title(random);
				tags.add(first);
				for (int t = random.nextInt(3); t > 0; t--) tags.add(topic(random, topics));
				if (i > 0 && random.nextDouble() < 0.1) {
					var source = random.nextInt(i);
					sources.add(linkUrl(source, firstTopic(source, topics)));
				}
			} else {
				url = commentUrl(i);
				tags.add("internal");
				tags.add("plugin/comment");
				// Threads concentrate on a few popular Refs
				var parent = zipf(random, Math.max(1, links), 0.9);
				var parentUrl = linkUrl(parent, firstTopic(parent, topics));
				// jasper-ui comments source [parent, thread top], which are the same for top level comments
				sources.add(parentUrl);
				sources.add(parentUrl);
			}
			json.append("{\"url\":").append(quote(url))
				.append(",\"title\":").append(quote(title))
				.append(",\"comment\":").append(quote(text(random, i < links ? 2 : 3)))
				.append(",\"published\":").append(quote(now.minus(age).toString()))
				.append(",\"modified\":").append(quote(modified.plus(i - from, ChronoUnit.MICROS).toString()))
				.append(",\"tags\":").append(array(tags))
				.append(",\"sources\":").append(array(sources))
				.append('}');
		}
		return json.append(']').toString();
	}

	/**
	 * Start of a run of distinct modified timestamps, one microsecond apart.
	 * Pushed entities must have unique modified timestamps per origin, and Refs modified
	 * in the last few minutes get their metadata generated on push.
	 */
	static synchronized Instant uniqueModified(int count) {
		var start = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(1);
		if (!start.isAfter(lastModified)) start = lastModified.plus(1, ChronoUnit.MICROS);
		lastModified = start.plus(count, ChronoUnit.MICROS);
		return start;
	}
	private static Instant lastModified = Instant.EPOCH;

	/** Replays the topic draw of link i so comments can point at it. */
	static String firstTopic(int i, List<String> topics) {
		var random = new Random(i * 7919L);
		zipf(random, LoadConfig.USERS, 1.0);
		random.nextDouble();
		random.nextDouble();
		return topic(random, topics);
	}

	/**
	 * JSON array of Exts for every topic.
	 */
	public static String exts(List<String> topics) {
		var modified = uniqueModified(topics.size());
		var json = new StringBuilder("[");
		for (int i = 0; i < topics.size(); i++) {
			var topic = topics.get(i);
			if (i > 0) json.append(',');
			json.append("{\"tag\":").append(quote(topic))
				.append(",\"name\":").append(quote(displayName(topic)))
				.append(",\"modified\":").append(quote(modified.plus(i, ChronoUnit.MICROS).toString()))
				.append('}');
		}
		return json.append(']').toString();
	}

	public static String displayName(String topic) {
		var name = topic.substring(topic.lastIndexOf('/') + 1);
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	public static String quote(String value) {
		var sb = new StringBuilder("\"");
		for (var c : value.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				default -> {
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
				}
			}
		}
		return sb.append('"').toString();
	}

	public static String array(Iterable<String> values) {
		var sb = new StringBuilder("[");
		for (var v : values) {
			if (sb.length() > 1) sb.append(',');
			sb.append(quote(v));
		}
		return sb.append(']').toString();
	}
}
