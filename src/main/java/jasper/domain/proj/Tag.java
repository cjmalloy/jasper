package jasper.domain.proj;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

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

	static boolean publicTag(String tag) {
		if (isBlank(tag)) return false;
		return !tag.startsWith("_") && !tag.startsWith("+");
	}

	static String localTag(String tag) {
		if (isBlank(tag)) return tag;
		if (!tag.contains("@")) return tag;
		return tag.substring(0, tag.indexOf("@"));
	}

	static String tagOrigin(String tag) {
		if (isBlank(tag)) return "";
		if (!tag.contains("@")) return "";
		return tag.substring(tag.indexOf("@"));
	}

	static String defaultOrigin(String tag, String origin) {
		if (isBlank(tag)) return tag;
		if (tag.endsWith("@")) return localTag(tag);
		if (isNotBlank(tagOrigin(tag))) return tag;
		if (isBlank(origin)) return tag;
		if ("@".equals(origin)) return tag;
		return tag + origin;
	}

	static String reverseOrigin(String qualifiedTag) {
		var origin = tagOrigin(qualifiedTag);
		var tag = localTag(qualifiedTag);
		if (isBlank(origin)) return tag;
		if (isBlank(tag)) tag = "user";
		return origin.substring(1) + "/" + tag;
	}
}
