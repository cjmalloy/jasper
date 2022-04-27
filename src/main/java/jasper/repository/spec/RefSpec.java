package jasper.repository.spec;

import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Expression;

import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.domain.proj.HasTags;
import org.springframework.data.jpa.domain.Specification;

public class RefSpec {

	public static Specification<Ref> none() {
		return (root, query, cb) -> cb.disjunction();
	}

	public static Specification<Ref> fulltextEn(String search, boolean rankedOrder) {
		return (root, query, cb) -> {
			var searchQuery = cb.function("websearch_to_tsquery", Object.class, cb.literal(search));
			if (rankedOrder) {
				query.orderBy(cb.desc(cb.function("ts_rank_cd", Object.class,
					root.get(Ref_.textsearchEn), searchQuery)));
			}
			return cb.isTrue(
				cb.function("ts_match_vq", Boolean.class,
					root.get(Ref_.textsearchEn),
					searchQuery));
		};
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

	public static Specification<Ref> hasResponse(String url) {
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists", Boolean.class,
				cb.function("jsonb_object_field", List.class,
					root.get(Ref_.metadata),
					cb.literal("responses")),
				cb.literal(url)));
	}

	public static Specification<Ref> hasInternalResponse(String url) {
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists", Boolean.class,
				cb.function("jsonb_object_field", List.class,
					root.get(Ref_.metadata),
					cb.literal("internalResponses")),
				cb.literal(url)));
	}

	public static Specification<Ref> hasNoSources() {
		return (root, query, cb) ->
			cb.or(
				cb.isNull(root.get(Ref_.sources)),
				cb.equal(
					cb.function("jsonb_array_length", Long.class, root.get(Ref_.sources)),
					cb.literal(0)));
	}

	public static Specification<Ref> hasNoResponses() {
		return (root, query, cb) ->
			cb.or(
				cb.isNull(root.get(Ref_.metadata)),
				cb.equal(
					cb.function("jsonb_array_length", Long.class,
						cb.function("jsonb_object_field", List.class,
							root.get(Ref_.metadata),
							cb.literal("responses"))),
					cb.literal(0)));
	}

	public static Specification<Ref> hasNoPluginResponses(String plugin) {
		return (root, query, cb) ->
			cb.or(
				cb.isNull(root.get(Ref_.metadata)),
				cb.equal(
					cb.function("jsonb_array_length", Long.class,
						cb.function("jsonb_object_field", List.class,
							cb.function("jsonb_object_field", List.class,
								root.get(Ref_.metadata),
								cb.literal("plugins")),
							cb.literal(plugin))),
					cb.literal(0)));
	}

	public static Specification<Ref> hasPluginResponses(String plugin) {
		return (root, query, cb) ->
			cb.and(
				cb.isNotNull(root.get(Ref_.metadata)),
				cb.gt(
					cb.function("jsonb_array_length", Long.class,
						cb.function("jsonb_object_field", List.class,
							cb.function("jsonb_object_field", List.class,
								root.get(Ref_.metadata),
								cb.literal("plugins")),
							cb.literal(plugin))),
					cb.literal(0)));
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

	public static <T extends HasTags> Specification<T> hasAnyQualifiedTag(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		var spec = Specification.<T>where(null);
		for (var t : tags) {
			spec = spec.or(t.refSpec());
		}
		return spec;
	}

	public static <T extends HasTags> Specification<T> hasAllQualifiedTags(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		var spec = Specification.<T>where(null);
		for (var t : tags) {
			spec = spec.and(t.refSpec());
		}
		return spec;
	}

	public static Expression<String[]> literal(CriteriaBuilder cb, List<String> tags) {
		return cb.function("string_to_array", String[].class,
			cb.literal(String.join(",", tags)),
			cb.literal(","));
	}
}
