package ca.hc.jasper.repository.spec;

import static ca.hc.jasper.repository.spec.OriginSpec.isOrigin;
import static ca.hc.jasper.repository.spec.RefSpec.hasTag;
import static ca.hc.jasper.repository.spec.TagSpec.isTag;

import ca.hc.jasper.domain.Origin;
import ca.hc.jasper.domain.Tag;
import ca.hc.jasper.domain.proj.*;
import org.springframework.data.jpa.domain.Specification;

public class QualifiedTag {
	public static final String TAG_OR_WILDCARD = "(" + Tag.REGEX + ")?";
	public static final String ORIGIN_OR_WILDCARD = "(" + Origin.REGEX_NOT_BLANK + "|@\\*)";
	public static final String SELECTOR = "(" + Tag.REGEX + "|" + TAG_OR_WILDCARD + ORIGIN_OR_WILDCARD + ")";
	public static final String REGEX = "!?" + SELECTOR;

	private final boolean not;
	private final String tag;
	private final String origin;

	public QualifiedTag(String qt) {
		not = qt.startsWith("!");
		if (not) qt = qt.substring(1);
		var index = qt.indexOf("@");
		if (index == -1) {
			tag = qt;
			origin = "";
		} else {
			tag = qt.substring(0, index);
			origin = qt.substring(index);
		}
	}

	public boolean captures(String capture) {
		var c = new QualifiedTag(capture);
		if (!tag.isEmpty() && !tag.equals(c.tag)) return not;
		if (!origin.equals("@*") && !origin.equals(c.origin)) return not;
		return !not;
	}

	public <T extends HasTags> Specification<T> refSpec() {
		Specification<T> spec = Specification.where(null);
		if (!tag.equals("")) spec = spec.and(hasTag(tag));
		if (!origin.equals("@*")) spec = spec.and(isOrigin(origin));
		return not ? Specification.not(spec) : spec;
	}

	public <T extends IsTag> Specification<T> spec() {
		Specification<T> spec = Specification.where(null);
		if (!tag.equals("")) spec = spec.and(isTag(tag));
		if (!origin.equals("@*")) spec = spec.and(isOrigin(origin));
		return not ? Specification.not(spec) : spec;
	}

	public <T extends HasOrigin> Specification<T> originSpec() {
		if (!tag.equals("")) throw new UnsupportedOperationException();
		if (origin.equals("@*")) throw new UnsupportedOperationException();
		return not ? Specification.not(isOrigin(origin)) : isOrigin(origin);
	}
}
