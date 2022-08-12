package jasper.domain.proj;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public interface HasTags extends HasOrigin {
	String REGEX = "[_+]?[a-z]+(/[a-z]+)*";
	String QTAG_REGEX = REGEX + HasOrigin.REGEX;
	String ADD_REMOVE_REGEX = "[-]?" + REGEX;
	int TAG_LEN = 64;
	int QTAG_LEN = TAG_LEN + ORIGIN_LEN + 1;

	String getUrl();
	List<String> getTags();

	@JsonIgnore
	default List<String> getQualifiedTags() {
		if (local()) return getTags();
		if (getTags() == null) return null;
		return getTags()
			.stream()
			.map(t -> t + getOrigin())
			.toList();
	}

	@JsonIgnore
	default List<String> getQualifiedNonPublicTags() {
		if (getTags() == null) return null;
		return getTags()
			.stream()
			.filter(t -> t.startsWith("_") || t.startsWith("+"))
			.map(t -> t + getOrigin())
			.toList();
	}
}
