package jasper.domain.proj;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

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
}
