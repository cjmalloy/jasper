package jasper.repository.spec;

import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.Tag;
import org.springframework.data.jpa.domain.Specification;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.hasTag;
import static jasper.repository.spec.TagSpec.isTag;
import static jasper.repository.spec.TemplateSpec.matchesTag;

public class QualifiedTag {
	public static final String SELECTOR = "(\\*|" + Tag.REGEX + "|(" + Tag.REGEX + ")?(" + HasOrigin.REGEX_NOT_BLANK + "|@\\*))";

	private final boolean not;
	private final String tag;
	private final String origin;

	public QualifiedTag(String qt) {
		not = qt.startsWith("!");
		if (not) qt = qt.substring(1);
		var index = qt.indexOf("@");
		if (index == -1) {
			if (qt.isEmpty()) throw new UnsupportedOperationException();
			tag = qt.equals("*") ? "" : qt;
			origin = "";
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

	public Specification<Ref> refSpec() {
		var spec = Specification.<Ref>where(null);
		if (!tag.equals("")) spec = spec.and(hasTag(tag));
		if (!origin.equals("@*")) spec = spec.and(isOrigin(origin));
		return not ? Specification.not(spec) : spec;
	}

	public <T extends Tag> Specification<T> spec() {
		var spec = Specification.<T>where(null);
		if (!tag.equals("")) spec = spec.and(isTag(tag));
		if (!origin.equals("@*")) spec = spec.and(isOrigin(origin));
		return not ? Specification.not(spec) : spec;
	}

	public Specification<Template> templateSpec() {
		var spec = Specification.<Template>where(null);
		if (!tag.equals("")) spec = spec.and(matchesTag(tag));
		if (!origin.equals("@*")) spec = spec.and(isOrigin(origin));
		return not ? Specification.not(spec) : spec;
	}
}
