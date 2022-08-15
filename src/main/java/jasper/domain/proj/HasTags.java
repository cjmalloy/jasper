package jasper.domain.proj;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

public interface HasTags extends HasOrigin {
	String getUrl();
	List<String> getTags();

	@JsonIgnore
	default List<String> getQualifiedTags() {
		if (isBlank(getOrigin())) return getTags();
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
