package jasper.domain.proj;

public interface HasOrigin extends HasModified {
	String REGEX_NOT_BLANK = "@[a-z]+(\\.[a-z])*";
	String REGEX = "(" + REGEX_NOT_BLANK + ")?";
	int ORIGIN_LEN = 64;

	String getOrigin();
	default boolean local() {
		return getOrigin() == null || getOrigin().isEmpty();
	}
}
