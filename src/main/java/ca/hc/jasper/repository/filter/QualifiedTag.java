package ca.hc.jasper.repository.filter;

import ca.hc.jasper.domain.Origin;
import ca.hc.jasper.domain.Tag;

public class QualifiedTag {
	public static final String TAG_OR_WILDCARD = "(" + Tag.REGEX + "|\\*)";
	public static final String ORIGIN_OR_WILDCARD = "(" + Origin.REGEX + "|@\\*)";
	public static final String REGEX = TAG_OR_WILDCARD + ORIGIN_OR_WILDCARD;

	private final String tag;

	public QualifiedTag(String tag) {
		this.tag = tag;
	}

	public String getTag() {
		if (!tag.contains("@")) return tag;
		return tag.split("@")[0];
	}

	public String getOrigin() {
		if (!tag.contains("@")) return "";
		return tag.split("@")[1];
	}
}
