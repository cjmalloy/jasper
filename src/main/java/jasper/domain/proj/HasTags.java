package jasper.domain.proj;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

import static jasper.domain.proj.Tag.matchesTag;
import static org.apache.commons.lang3.StringUtils.isBlank;

public interface HasTags extends Cursor {
	String getTitle();
	String getUrl();
	List<String> getTags();
	void setTags(List<String> tags);
	ObjectNode getPlugins();
	void setPlugins(ObjectNode plugins);

	static String formatTag(Object tag) {
		if (isBlank((String) tag)) return "~";
		return tag.toString();
	}

	static boolean hasMedia(HasTags hasTags) {
		if (hasTags == null) return false;
		if (hasTags.getTags() == null) return false;
		return hasTags.getTags().contains("plugin/audio") ||
			hasTags.getTags().contains("plugin/video") ||
			hasTags.getTags().contains("plugin/image") ||
			hasTags.getTags().contains("plugin/embed");
	}

	static boolean hasMatchingTag(HasTags hasTags, String prefix) {
		return hasTags.getTags().stream().anyMatch(t -> matchesTag(prefix, t));
	}

	static boolean author(String tag) {
		if (isBlank(tag)) return false;
		return "+user".equals(tag)
			|| "_user".equals(tag)
			|| tag.startsWith("+user/")
			|| tag.startsWith("_user/");
	}

	static List<String> authors(HasTags hasTags) {
		if (hasTags == null) return List.of();
		if (hasTags.getTags() == null) return List.of();
		return hasTags.getTags().stream()
					  .filter(HasTags::author)
					  .toList();
	}

	static String prefix(String prefix, String ...rest) {
		StringBuilder result = new StringBuilder();
		if (isPub(prefix) && !isPub(rest[0])) {
			result.append(rest[0].charAt(0) + prefix);
		} else {
			result.append(prefix);
		}
		for (var r : rest) {
			result
				.append("/")
				.append(pub(r));
		}
		return result.toString().replace("//", "/");
	}

	static String pub(String tag) {
		if (isPub(tag)) return tag;
		return tag.substring(1);
	}

	static boolean isPub(String tag) {
		return !tag.startsWith("_") && !tag.startsWith("+");
	}
}
