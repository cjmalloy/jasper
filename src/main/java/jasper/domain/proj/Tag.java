package jasper.domain.proj;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

public interface Tag extends Cursor {
	String REGEX = "[_+]?[a-z0-9]+(?:[./][a-z0-9]+)*";
	String QTAG_REGEX = REGEX + HasOrigin.REGEX;
	String ADD_REMOVE_REGEX = "[-]?" + REGEX;
	int TAG_LEN = 64;
	int QTAG_LEN = TAG_LEN + ORIGIN_LEN + 1;

	String getTag();
	void setTag(String tag);
	String getName();

	@JsonIgnore
	default String getQualifiedTag() {
		return getTag() + getOrigin();
	}

	static String urlForUser(String url, String user) {
		return "tag:/" + user + "?url=" + url;
	}

	static void removeTag(List<String> tags, String tag) {
		for (var i = tags.size() - 1; i >= 0; i--) {
			var t = tags.get(i);
			if (t.equals(tag) || t.startsWith(tag + "/")) {
				tags.remove(i);
			}
		}
	}

	static String localTag(String tag) {
		if (isBlank(tag)) return tag;
		if (!tag.contains("@")) return tag;
		return tag.substring(0, tag.indexOf("@"));
	}

	static String tagOrigin(String tag) {
		if (isBlank(tag)) return tag;
		if (!tag.contains("@")) return "";
		return tag.substring(tag.indexOf("@"));
	}
}
