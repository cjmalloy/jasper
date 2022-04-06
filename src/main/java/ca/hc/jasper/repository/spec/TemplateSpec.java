package ca.hc.jasper.repository.spec;

import java.util.List;

import ca.hc.jasper.domain.Template;
import ca.hc.jasper.domain.Template_;
import org.springframework.data.jpa.domain.Specification;

public class TemplateSpec {

	public static Specification<Template> defaultTemplate() {
		return (root, query, cb) ->
			cb.equal(
				root.get(Template_.prefix),
				"");
	}

	public static Specification<Template> isTag(String tag) {
		return (root, query, cb) ->
			cb.equal(
				root.get(Template_.prefix),
				tag);
	}

	public static Specification<Template> matchesTag(String tag) {
		return (root, query, cb) ->
			cb.not(
				cb.like(
					root.get(Template_.prefix),
					tag + "/%"));
	}

	public static Specification<Template> matchesAnyQualifiedTag(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		var spec = Specification.<Template>where(null);
		for (var t : tags) {
			spec = spec.or(t.templateSpec());
		}
		return spec;
	}

	public static Specification<Template> matchesAllQualifiedTag(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		var spec = Specification.<Template>where(null);
		for (var t : tags) {
			spec = spec.and(t.templateSpec());
		}
		return spec;
	}
}
