package jasper.domain.proj;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

public interface HasTags extends Cursor {
	String getUrl();
	List<String> getTags();
	void setTags(List<String> tags);
	ObjectNode getPlugins();
	void setPlugins(ObjectNode plugins);

	static boolean hasMedia(HasTags hasTags) {
		if (hasTags == null) return false;
		if (hasTags.getTags() == null) return false;
		return hasTags.getTags().contains("plugin/audio") ||
			hasTags.getTags().contains("plugin/video") ||
			hasTags.getTags().contains("plugin/image") ||
			hasTags.getTags().contains("plugin/embed");
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
}
