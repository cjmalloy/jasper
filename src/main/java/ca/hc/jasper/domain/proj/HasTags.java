package ca.hc.jasper.domain.proj;

import java.util.List;

public interface HasTags extends HasOrigin {
	List<String> getTags();
	default List<String> getQualifiedTags() {
		if (getTags() == null) return null;
		if (local()) return getTags();
		return getTags().stream().map(t -> t + getOrigin()).toList();
	}
}
