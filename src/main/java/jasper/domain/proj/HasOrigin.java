package jasper.domain.proj;

import static org.apache.commons.lang3.StringUtils.isBlank;

public interface HasOrigin {
	String REGEX_NOT_BLANK = "@[a-z0-9]+(?:[.][a-z0-9]+)*";
	String REGEX = "(?:" + REGEX_NOT_BLANK + ")?";
	int ORIGIN_LEN = 64;

	String getOrigin();
	void setOrigin(String origin);

	static String origin(String origin) {
		if (origin == null) return "";
		return origin;
	}

	static String formatOrigin(String origin) {
		if (isBlank(origin)) return "default";
		return origin;
	}

	static String subOrigin(String local, String origin) {
		if (local == null) local = "";
		if (origin == null) origin = "";
		if (isBlank(local)) return origin;
		if (isBlank(origin)) return local;
		if (origin.startsWith("@")) origin = origin.substring(1);
		return local + '.' + origin;
	}
}
