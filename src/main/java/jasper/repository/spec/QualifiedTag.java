package jasper.repository.spec;

import jasper.domain.Ref;
import jasper.domain.Template;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.Tag;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.stream.Collectors;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.hasTag;
import static jasper.repository.spec.TagSpec.isTag;
import static jasper.repository.spec.TemplateSpec.matchesTag;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public class QualifiedTag {
	public static final String SELECTOR = "(?:\\*|" + Tag.REGEX + "|(?:" + Tag.REGEX + ")?(?:" + HasOrigin.REGEX_NOT_BLANK + "|@\\*))";

	public final boolean not;
	public final String tag;
	public final String origin;

	protected QualifiedTag(String qt) {
		not = qt.startsWith("!");
		if (not) qt = qt.substring(1);
		var index = qt.indexOf("@");
		if (index == -1) {
			tag = qt.equals("*") ? "" : qt;
			origin = "";
		} else {
			tag = qt.substring(0, index);
			origin = qt.substring(index);
			if (origin.equals("@")) throw new UnsupportedOperationException();
		}
	}

	@Override
	public String toString() {
		return (not ? "!" : "") + tag + origin;
	}

	public boolean captures(String capture) {
		return captures(selector(capture));
	}

	public boolean captures(QualifiedTag c) {
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

	public static QualifiedTag selector(String qt) {
		if (qt.startsWith("!")) throw new UnsupportedOperationException();
		return new QualifiedTag(qt);
	}

	public static QualifiedTag originSelector(String qt) {
		if (qt.isEmpty()) return selector("*");
		return selector(qt);
	}

	public static String concat(String ...tags) {
		var result = new StringBuilder();
		for (var tag : tags) {
			if (isEmpty(tag)) continue;
			if (tag.startsWith("+") || tag.startsWith("_") || tag.startsWith("@")) tag = tag.substring(1);
			if (result.length() > 0) result.append("/");
			result.append(tag);
		}
		return result.toString();
	}

	public static QualifiedTag atom(String qt) {
		return new QualifiedTag(qt);
	}

	public static List<QualifiedTag> qtList(String defaultOrigin, List<String> tags) {
		return tags.stream()
			.map(t -> t.contains("@") ? t : t + defaultOrigin)
			.map(QualifiedTag::new)
			.collect(Collectors.toList());
	}
}
