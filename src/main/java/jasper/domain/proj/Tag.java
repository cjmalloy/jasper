package jasper.domain.proj;

public interface IsTag extends HasOrigin {
	String REGEX = "[_+]?[a-z]+(/[a-z]+)*";
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
}
