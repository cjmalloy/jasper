package jasper.domain.proj;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public interface Tag extends HasOrigin {
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
}
