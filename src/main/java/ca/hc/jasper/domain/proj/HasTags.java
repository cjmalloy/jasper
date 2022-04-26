package ca.hc.jasper.domain.proj;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface HasTags extends HasOrigin {
	String getUrl();
	List<String> getTags();

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
