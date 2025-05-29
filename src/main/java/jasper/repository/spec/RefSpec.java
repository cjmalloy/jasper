package jasper.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static jasper.domain.proj.Tag.isPublicTag;
import static jasper.domain.proj.Tag.publicTag;
import static jasper.repository.spec.OriginSpec.none;

public class RefSpec {

	public static Specification<Ref> fulltextEn(String search, boolean rankedOrder) {
		return (root, query, cb) -> {
			var searchQuery = cb.function("websearch_to_tsquery", Object.class, cb.literal(search));
			if (rankedOrder) {
				query.orderBy(cb.desc(cb.function("ts_rank_cd", Object.class,
					root.get(Ref_.textsearchEn),
					searchQuery)));
			}
			return cb.isTrue(
				cb.function("ts_match_vq", Boolean.class,
					root.get(Ref_.textsearchEn),
					searchQuery));
		};
	}

	public static Specification<Ref> isUrl(String url) {
		return (root, query, cb) ->
			cb.or(
				cb.equal(
					root.get(Ref_.url),
					url),
				cb.isTrue(
					cb.function("jsonb_exists", Boolean.class,
						root.get(Ref_.alternateUrls),
						cb.literal(url))));
	}

	public static Specification<Ref> isNotObsolete() {
		return (root, query, cb) ->
			cb.or(
				cb.isNull(root.get(Ref_.metadata)),
				cb.isNull(cb.function("jsonb_object_field_text", String.class,
					root.get(Ref_.metadata),
					cb.literal("obsolete"))),
				cb.notEqual(cb.function("jsonb_object_field_text", String.class,
					root.get(Ref_.metadata),
					cb.literal("obsolete")),
					cb.literal("true")));
	}

	public static Specification<Ref> isScheme(String scheme) {
		return (root, query, cb) ->
			cb.like(
				root.get(Ref_.url),
				cb.literal(scheme + "%"));
	}

	public static Specification<Ref> isUrls(List<String> urls) {
		if (urls == null || urls.isEmpty()) return none();
		return (root, query, cb) ->
			cb.or(
				root.get(Ref_.url)
					.in(urls),
				cb.isTrue(
					cb.function("jsonb_exists_any", Boolean.class,
						root.get(Ref_.alternateUrls),
						literal(cb, urls))));
	}

	public static Specification<Ref> endsWithTitle(String text) {
		return (root, query, cb) ->
			cb.like(
				cb.literal(text.toLowerCase()),
				cb.concat("%", cb.lower(root.get(Ref_.title))));
	}

	public static Specification<Ref> hasSource(String url) {
		return (root, query, cb) -> cb.and(
			cb.notEqual(root.get(Ref_.url), url),
			cb.function("jsonb_exists", Boolean.class,
				root.get(Ref_.sources),
				cb.literal(url)));
	}

	public static Specification<Ref> hasAlternateUrl(String url) {
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists", Boolean.class,
				root.get(Ref_.alternateUrls),
				cb.literal(url)));
	}

	public static Specification<Ref> hasResponse(String url) {
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists", Boolean.class,
				cb.function("jsonb_object_field", Object.class,
					root.get(Ref_.metadata),
					cb.literal("responses")),
				cb.literal(url)));
	}

	public static Specification<Ref> hasInternalResponse(String url) {
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists", Boolean.class,
				cb.function("jsonb_object_field", Object.class,
					root.get(Ref_.metadata),
					cb.literal("internalResponses")),
				cb.literal(url)));
	}

	public static Specification<Ref> hasNoTags() {
		return (root, query, cb) ->
			cb.or(
				cb.isNull(root.get(Ref_.tags)),
				cb.equal(
					cb.function("jsonb_array_length", Long.class, root.get(Ref_.tags)),
					cb.literal(0)));
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
				cb.isNull(
					cb.function("jsonb_object_field", Object.class,
						root.get(Ref_.metadata),
						cb.literal("responses"))),
				cb.equal(
					cb.function("jsonb_array_length", Long.class,
						cb.function("jsonb_object_field", Object.class,
							root.get(Ref_.metadata),
							cb.literal("responses"))),
					cb.literal(0)));
	}

	public static Specification<Ref> hasNoPluginResponses(String plugin) {
		return (root, query, cb) ->
			cb.or(
				cb.isNull(root.get(Ref_.metadata)),
				cb.isNull(
					cb.function("jsonb_object_field", Object.class,
						root.get(Ref_.metadata),
						cb.literal("plugins"))),
				cb.isFalse(
					cb.function("jsonb_exists", Boolean.class,
						cb.function("jsonb_object_field", Object.class,
							root.get(Ref_.metadata),
							cb.literal("plugins")),
						cb.literal(plugin))));
	}

	public static Specification<Ref> hasPluginResponses(String plugin) {
		return (root, query, cb) ->
			cb.and(
				cb.isNotNull(root.get(Ref_.metadata)),
				cb.function("jsonb_exists", Boolean.class,
					cb.function("jsonb_object_field", Object.class,
						root.get(Ref_.metadata),
						cb.literal("plugins")),
					cb.literal(plugin)));
	}

	public static Specification<Ref> hasNoPluginResponses(String user, String plugin) {
		return (root, query, cb) ->
			cb.or(
				cb.isNull(root.get(Ref_.metadata)),
				cb.isNull(
					cb.function("jsonb_object_field", Object.class,
						root.get(Ref_.metadata),
						cb.literal("userUrls"))),
				cb.isNull(
					cb.function("jsonb_object_field", Object.class,
						cb.function("jsonb_object_field", Object.class,
							root.get(Ref_.metadata),
							cb.literal("userUrls")),
						cb.literal(plugin))),
				cb.isFalse(
					cb.function("jsonb_exists", Boolean.class,
						cb.function("jsonb_object_field", Object.class,
							cb.function("jsonb_object_field", Object.class,
								root.get(Ref_.metadata),
								cb.literal("userUrls")),
							cb.literal(plugin)),
						cb.concat("tag:/" + user + "?url=", root.get(Ref_.url)))));
	}

	public static Specification<Ref> hasPluginResponses(String user, String plugin) {
		return (root, query, cb) ->
			cb.and(
				cb.isNotNull(root.get(Ref_.metadata)),
				cb.isTrue(
					cb.function("jsonb_exists", Boolean.class,
						cb.function("jsonb_object_field", Object.class,
							cb.function("jsonb_object_field", Object.class,
								root.get(Ref_.metadata),
								cb.literal("userUrls")),
							cb.literal(plugin)),
						cb.concat("tag:/" + user + "?url=", root.get(Ref_.url)))));
	}

	private static Expression<Object> getTagsExpression(Root<Ref> root, CriteriaBuilder cb) {
		return cb.function("COALESCE", Object.class,
			cb.function("jsonb_object_field", Object.class,
				root.get(Ref_.metadata),
				cb.literal("expandedTags")),
			root.get(Ref_.tags),
			cb.literal("[]")
		);
	}

	public static Specification<Ref> hasTag(String tag) {
		return (root, query, cb) -> cb.isTrue(
			cb.function("jsonb_exists", Boolean.class,
				getTagsExpression(root, cb),
				cb.literal(tag)));
	}

	public static Specification<Ref> hasNoChildTag(String tag) {
		return (root, query, cb) -> cb.isFalse(
			cb.like(
				cb.function("jsonb_extract_path_text", String.class,
					root.get(Ref_.tags),
					cb.literal("{}")),
				"%\"" + tag + "/%"));
	}

	public static Specification<Ref> hasDownwardTag(String tag) {
		if (isPublicTag(tag)) {
			return (root, query, cb) -> cb.isTrue(
				cb.function("jsonb_exists", Boolean.class,
					getTagsExpression(root, cb),
					cb.literal(tag)));
		} else if (tag.startsWith("_")) {
			return (root, query, cb) -> cb.isTrue(
				cb.or(
					cb.function("jsonb_exists", Boolean.class,
						getTagsExpression(root, cb),
						cb.literal(tag)),
				cb.or(
					cb.function("jsonb_exists", Boolean.class,
						getTagsExpression(root, cb),
						cb.literal("+" + publicTag(tag))),
					cb.function("jsonb_exists", Boolean.class,
						getTagsExpression(root, cb),
						cb.literal(publicTag(tag))))
				));
		} else {
			// Protected tag
			return (root, query, cb) -> cb.isTrue(
				cb.or(
					cb.function("jsonb_exists", Boolean.class,
						getTagsExpression(root, cb),
						cb.literal(tag)),
					cb.function("jsonb_exists", Boolean.class,
						getTagsExpression(root, cb),
						cb.literal(publicTag(tag)))
				));
		}
	}

	public static Specification<Ref> hasAnyQualifiedTag(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		var spec = Specification.<Ref>where(null);
		for (var t : tags) {
			spec = spec.or(t.refSpec());
		}
		return spec;
	}

	public static Specification<Ref> hasAllQualifiedTags(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return null;
		var spec = Specification.<Ref>where(null);
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

	public static Specification<Ref> isPublishedAfter(Instant i) {
		if (i == null) return null;
		return (root, query, cb) ->
				cb.greaterThan(
						root.get(Ref_.published),
						i);
	}

	public static Specification<Ref> isPublishedBefore(Instant i) {
		if (i == null) return null;
		return (root, query, cb) ->
				cb.lessThan(
						root.get(Ref_.published),
						i);
	}

	public static Specification<Ref> isCreatedAfter(Instant i) {
		if (i == null) return null;
		return (root, query, cb) ->
				cb.greaterThan(
						root.get(Ref_.created),
						i);
	}

	public static Specification<Ref> isCreatedBefore(Instant i) {
		if (i == null) return null;
		return (root, query, cb) ->
				cb.lessThan(
						root.get(Ref_.created),
						i);
	}

	public static Specification<Ref> isResponseAfter(Instant i) {
		if (i == null) return null;
		return (root, query, cb) ->
			cb.greaterThan(cb.function("jsonb_object_field_text", String.class,
					root.get(Ref_.metadata),
					cb.literal(Ref_.MODIFIED)),
				cb.literal(i.toString()));
	}

	public static Specification<Ref> isResponseBefore(Instant i) {
		if (i == null) return null;
		return (root, query, cb) ->
			cb.lessThan(cb.function("jsonb_object_field_text", String.class,
					root.get(Ref_.metadata),
					cb.literal(Ref_.MODIFIED)),
				cb.literal(i.toString()));
	}
}
