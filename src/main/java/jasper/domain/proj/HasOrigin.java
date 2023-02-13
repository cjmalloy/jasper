package jasper.domain.proj;

public interface HasOrigin extends HasModified {
	String REGEX_NOT_BLANK = "@[a-z0-9]+(?:[.][a-z0-9]+)*";
	String REGEX = "(?:" + REGEX_NOT_BLANK + ")?";
	int ORIGIN_LEN = 64;

	String getOrigin();
	void setOrigin(String origin);
}
