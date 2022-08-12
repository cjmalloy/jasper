package jasper.repository.spec;

import jasper.domain.Template;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.HasTags;
import jasper.domain.proj.IsTag;
import jasper.repository.filter.TagQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.hasTag;
import static jasper.repository.spec.TagSpec.isTag;
import static jasper.repository.spec.TemplateSpec.defaultTemplate;
import static jasper.repository.spec.TemplateSpec.matchesTag;

public class QualifiedTag {
	private static final Logger logger = LoggerFactory.getLogger(TagQuery.class);
	public static final String TAG_OR_WILDCARD = "(" + HasTags.REGEX + ")?";
	public static final String ORIGIN_OR_WILDCARD = "(" + HasOrigin.REGEX_NOT_BLANK + "|@\\*)";
	public static final String SELECTOR = "(" + HasTags.REGEX + "|" + TAG_OR_WILDCARD + ORIGIN_OR_WILDCARD + ")";

	private final boolean not;
	private final String tag;
	private final String origin;

	public QualifiedTag(String qt) {
		logger.debug(qt);
		not = qt.startsWith("!");
		if (not) qt = qt.substring(1);
		var index = qt.indexOf("@");
		if (index == -1) {
			tag = qt;
			origin = "";
			if (tag.isEmpty()) throw new UnsupportedOperationException();
		} else {
			tag = qt.substring(0, index);
			origin = qt.substring(index);
			if (origin.equals("@")) throw new UnsupportedOperationException();
		}
	}

	public boolean captures(String capture) {
		var c = new QualifiedTag(capture);
		if (!tag.isEmpty() && !(tag.equals(c.tag) || c.tag.startsWith(tag + "/"))) return not;
		if (!origin.equals("@*") && !origin.equals(c.origin)) return not;
		return !not;
	}

	public <T extends HasTags> Specification<T> refSpec() {
		var spec = Specification.<T>where(null);
		if (!tag.equals("")) spec = spec.and(hasTag(tag));
		if (!origin.equals("@*")) spec = spec.and(isOrigin(origin));
		return not ? Specification.not(spec) : spec;
	}

	public <T extends IsTag> Specification<T> spec() {
		var spec = Specification.<T>where(null);
		if (!tag.equals("")) spec = spec.and(isTag(tag));
		if (!origin.equals("@*")) spec = spec.and(isOrigin(origin));
		return not ? Specification.not(spec) : spec;
	}

	public Specification<Template> templateSpec() {
		var spec = Specification.<Template>where(null);
		if (tag.equals("")) spec = spec.and(defaultTemplate());
		if (!tag.equals("")) spec = spec.and(matchesTag(tag));
		if (!origin.equals("@*")) spec = spec.and(isOrigin(origin));
		return not ? Specification.not(spec) : spec;
	}

	public <T extends HasOrigin> Specification<T> originSpec() {
		if (!tag.equals("")) throw new UnsupportedOperationException();
		if (origin.equals("@*")) throw new UnsupportedOperationException();
		return not ? Specification.not(isOrigin(origin)) : isOrigin(origin);
	}
}
