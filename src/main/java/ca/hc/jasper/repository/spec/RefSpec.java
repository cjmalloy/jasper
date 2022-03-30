package ca.hc.jasper.repository.spec;

import static ca.hc.jasper.repository.spec.OriginSpec.isOrigin;

import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.domain.Ref_;
import ca.hc.jasper.domain.proj.HasTags;
import ca.hc.jasper.repository.filter.QualifiedTag;
import org.springframework.data.jpa.domain.Specification;

public class RefSpec {

	public static Specification<Ref> none() {
		return (root, query, cb) -> cb.disjunction();
	}

	public static Specification<Ref> isUrl(String url) {
		return (root, query, cb) ->
			cb.equal(
				root.get(Ref_.url),
				url);
	}

	public static Specification<Ref> isUrls(List<String> urls) {
		if (urls == null || urls.isEmpty()) return none();
		return (root, query, cb) ->
			root.get(Ref_.url)
				.in(urls);
	}

	public static Specification<Ref> hasSource(String url) {
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists", Boolean.class,
				root.get(Ref_.sources),
				cb.literal(url)));
	}

	public static <T extends HasTags> Specification<T> hasTag(String tag) {
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists", Boolean.class,
				root.get("tags"),
				cb.literal(tag)));
	}

	public static <T extends HasTags> Specification<T> hasAnyTag(List<String> tags) {
		if (tags == null || tags.isEmpty()) return null;
		if (tags.size() == 1) return hasTag(tags.get(0));
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists_any", Boolean.class,
				root.get("tags"),
				literal(cb, tags)));
	}

	public static <T extends HasTags> Specification<T> hasAllTags(List<String> tags) {
		if (tags == null || tags.isEmpty()) return null;
		if (tags.size() == 1) return hasTag(tags.get(0));
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists_all", Boolean.class,
				root.get("tags"),
				literal(cb, tags)));
	}

	public static <T extends HasTags> Specification<T> hasQualifiedTag(QualifiedTag tag) {
		if (tag.getOrigin().equals("*")) return hasTag(tag.getTag());
		if (tag.getTag().equals("*")) return isOrigin(tag.getOrigin());
		return Specification
			.<T>where(hasTag(tag.getTag()))
			.and(isOrigin(tag.getOrigin()));
	}

	public static <T extends HasTags> Specification<T> hasAnyQualifiedTag(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		if (tags.size() == 1) return hasQualifiedTag(tags.get(0));
		var spec = Specification.<T>where(null);
		for (var t : tags) {
			spec.or(hasQualifiedTag(t));
		}
		return spec;
	}

	public static <T extends HasTags> Specification<T> hasAllQualifiedTags(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		if (tags.size() == 1) return hasQualifiedTag(tags.get(0));
		var spec = Specification.<T>where(null);
		for (var t : tags) {
			spec.and(hasQualifiedTag(t));
		}
		return spec;
	}

	public static Expression<String[]> literal(CriteriaBuilder cb, List<String> tags) {
		return cb.function("string_to_array", String[].class,
			cb.literal(String.join(",", tags)),
			cb.literal(","));
	}
}
