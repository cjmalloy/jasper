package jasper.domain.proj;

public interface Tag extends HasOrigin {
	String REGEX = "[_+]?[a-z0-9]+(?:[./][a-z0-9]+)*";
	String QTAG_REGEX = REGEX + HasOrigin.REGEX;
	String ADD_REMOVE_REGEX = "[-]?" + REGEX;
	int TAG_LEN = 64;
	int QTAG_LEN = TAG_LEN + ORIGIN_LEN + 1;

	String getTag();
	void setTag(String tag);
	String getName();

	default String getQualifiedTag() {
		return getTag() + getOrigin();
	}

	static String urlForUser(String plugin, String user) {
		return "tag:/" + plugin + "?user=" + user;
	}
}
