package jasper.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.repository.spec.SortSpec.PluginValueFilter;
import jasper.repository.spec.SortSpec.TagValueFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static jasper.domain.Metadata.timestamp;
import static jasper.domain.proj.Tag.isPublicTag;
import static jasper.domain.proj.Tag.publicTag;
import static jasper.domain.proj.Tag.userUrlPrefix;
import static jasper.repository.spec.OriginSpec.none;
import static jasper.repository.spec.SortSpec.compareJsonbValue;
import static jasper.repository.spec.SortSpec.createJsonbOrder;
import static jasper.repository.spec.SortSpec.createJsonbValue;
import static jasper.repository.spec.SortSpec.isJsonbSortProperty;
import static org.springframework.data.jpa.domain.Specification.unrestricted;

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

	public static Specification<Ref> isNotIgnored() {
		return (root, query, cb) ->
			cb.or(
				cb.isNull(root.get(Ref_.metadata)),
				cb.isNull(cb.function("jsonb_object_field_text", String.class,
					root.get(Ref_.metadata),
					cb.literal("ignored"))),
				cb.notEqual(cb.function("jsonb_object_field_text", String.class,
					root.get(Ref_.metadata),
					cb.literal("ignored")),
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

	/**
	 * Same expression as sorting by "metadata->plugins->{plugin}", so hot response indexes apply.
	 */
	public static Specification<Ref> hasNoPluginResponses(String plugin) {
		return (root, query, cb) -> cb.equal(
			cb.coalesce(
				cb.function("safe_numeric", Double.class,
					cb.function("jsonb_object_field", Object.class,
						cb.function("jsonb_object_field", Object.class,
							root.get(Ref_.metadata),
							cb.literal("plugins")),
						cb.literal(plugin))),
				cb.literal(0.0)),
			cb.function("cast_to_numeric", Double.class, cb.literal(0)));
	}

	/**
	 * Same expression as sorting by "metadata->plugins->{plugin}", so hot response indexes apply.
	 */
	public static Specification<Ref> hasPluginResponses(String plugin) {
		return (root, query, cb) -> cb.greaterThan(
			cb.coalesce(
				cb.function("safe_numeric", Double.class,
					cb.function("jsonb_object_field", Object.class,
						cb.function("jsonb_object_field", Object.class,
							root.get(Ref_.metadata),
							cb.literal("plugins")),
						cb.literal(plugin))),
				cb.literal(0.0)),
			cb.function("cast_to_numeric", Double.class, cb.literal(0)));
	}

	/**
	 * Same expression as sorting by "metadata->remotePlugins->{plugin}", so hot remote response indexes apply.
	 */
	public static Specification<Ref> hasRemotePluginResponses(String plugin) {
		return (root, query, cb) -> cb.greaterThan(
			cb.coalesce(
				cb.function("safe_numeric", Double.class,
					cb.function("jsonb_object_field", Object.class,
						cb.function("jsonb_object_field", Object.class,
							root.get(Ref_.metadata),
							cb.literal("remotePlugins")),
						cb.literal(plugin))),
				cb.literal(0.0)),
			cb.function("cast_to_numeric", Double.class, cb.literal(0)));
	}

	public static Specification<Ref> hasNoPluginResponses(String user, String userOrigin, String plugin) {
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
						cb.concat(userUrlPrefix(user, userOrigin), root.get(Ref_.url)))));
	}

	public static Specification<Ref> hasPluginResponses(String user, String userOrigin, String plugin) {
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
						cb.concat(userUrlPrefix(user, userOrigin), root.get(Ref_.url)))));
	}

	private static Predicate tagExists(Root<Ref> root, CriteriaBuilder cb, String tag) {
		var expanded = cb.function("jsonb_expanded_tags", Object.class, root.get(Ref_.metadata));
		return cb.or(
			cb.and(
				cb.isNotNull(expanded),
				cb.isTrue(cb.function("jsonb_exists", Boolean.class, expanded, cb.literal(tag)))),
			cb.and(
				cb.isNull(expanded),
				cb.isNotNull(root.get(Ref_.tags)),
				cb.or(
					cb.isTrue(cb.function("jsonb_exists", Boolean.class, root.get(Ref_.tags), cb.literal(tag))),
					cb.like(
						cb.function("jsonb_text", String.class, root.get(Ref_.tags)),
						"%\"" + tag.replace("_", "\\_") + "/%",
						'\\'))));
	}

	public static Specification<Ref> hasTag(String tag) {
		return (root, query, cb) -> tagExists(root, cb, tag);
	}

	public static Specification<Ref> hasDownwardTag(String tag) {
		if (isPublicTag(tag)) {
			return (root, query, cb) ->
				tagExists(root, cb, tag);
		} else if (tag.startsWith("_")) {
			return (root, query, cb) -> cb.or(
				tagExists(root, cb, tag),
				tagExists(root, cb, "+" + publicTag(tag)),
				tagExists(root, cb, publicTag(tag)));
		} else {
			// Protected tag
			return (root, query, cb) -> cb.or(
				tagExists(root, cb, tag),
				tagExists(root, cb, publicTag(tag)));
		}
	}

	public static Specification<Ref> hasAnyQualifiedTag(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return unrestricted();
		var spec = Specification.<Ref>unrestricted();
		for (var t : tags) {
			spec = spec.or(t.refSpec());
		}
		return spec;
	}

	public static Specification<Ref> hasAllQualifiedTags(List<QualifiedTag> tags) {
		if (tags == null || tags.isEmpty()) return unrestricted();
		var spec = Specification.<Ref>unrestricted();
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
		if (i == null) return unrestricted();
		return (root, query, cb) ->
				cb.greaterThan(
						root.get(Ref_.published),
						i);
	}

	public static Specification<Ref> isPublishedBefore(Instant i) {
		if (i == null) return unrestricted();
		return (root, query, cb) ->
				cb.lessThan(
						root.get(Ref_.published),
						i);
	}

	public static Specification<Ref> isCreatedAfter(Instant i) {
		if (i == null) return unrestricted();
		return (root, query, cb) ->
				cb.greaterThan(
						root.get(Ref_.created),
						i);
	}

	public static Specification<Ref> isCreatedBefore(Instant i) {
		if (i == null) return unrestricted();
		return (root, query, cb) ->
				cb.lessThan(
						root.get(Ref_.created),
						i);
	}

	public static Specification<Ref> isResponseAfter(Instant i) {
		if (i == null) return unrestricted();
		return (root, query, cb) ->
			cb.greaterThan(cb.function("jsonb_object_field_text", String.class,
					root.get(Ref_.metadata),
					cb.literal("newResponse")),
				cb.literal(timestamp(i)));
	}

	public static Specification<Ref> isResponseBefore(Instant i) {
		if (i == null) return unrestricted();
		return (root, query, cb) ->
			cb.lessThan(cb.function("jsonb_object_field_text", String.class,
					root.get(Ref_.metadata),
					cb.literal("newResponse")),
				cb.literal(timestamp(i)));
	}

	public static Specification<Ref> isReactionAfter(Instant i) {
		if (i == null) return unrestricted();
		return (root, query, cb) ->
			cb.greaterThan(cb.function("jsonb_object_field_text", String.class,
					root.get(Ref_.metadata),
					cb.literal("newReaction")),
				cb.literal(timestamp(i)));
	}

	public static Specification<Ref> isReactionBefore(Instant i) {
		if (i == null) return unrestricted();
		return (root, query, cb) ->
			cb.lessThan(cb.function("jsonb_object_field_text", String.class,
					root.get(Ref_.metadata),
					cb.literal("newReaction")),
				cb.literal(timestamp(i)));
	}

	public static Specification<Ref> isTagValueBefore(TagValueFilter filter) {
		return isTagValue(filter, true);
	}

	public static Specification<Ref> isTagValueAfter(TagValueFilter filter) {
		return isTagValue(filter, false);
	}

	/**
	 * Compares the value of a tag in the tags array, using the same expression as tag value sorting
	 * so hot tag indexes apply. Refs without a value for the tag never match.
	 */
	private static Specification<Ref> isTagValue(TagValueFilter filter, boolean before) {
		if (filter == null) return unrestricted();
		return (root, query, cb) -> {
			var tag = cb.literal(filter.sort().tag());
			var text = cb.function("tag_value", String.class, root.get(Ref_.tags), tag);
			if (filter.value() instanceof String value) {
				return cb.and(
					cb.notEqual(text, ""),
					before ? cb.lessThan(text, value) : cb.greaterThan(text, value));
			}
			var number = cb.function(filter.sort().function(), Double.class, root.get(Ref_.tags), tag);
			var value = cb.function("cast_to_numeric", Double.class, cb.literal(filter.value()));
			return cb.and(
				cb.notEqual(text, ""),
				before ? cb.lessThan(number, value) : cb.greaterThan(number, value));
		};
	}

	public static Specification<Ref> isPluginValueBefore(PluginValueFilter filter) {
		return isPluginValue(filter, true);
	}

	public static Specification<Ref> isPluginValueAfter(PluginValueFilter filter) {
		return isPluginValue(filter, false);
	}

	/**
	 * Compares a field of plugin data, using the same expression as plugin sorting, so hot plugin indexes apply.
	 * Refs without a value for the field never match.
	 * Response counts ("metadata->plugins->{plugin}" or "metadata->remotePlugins->{plugin}") use the same
	 * expression as sorting by them and the pluginResponse filters, so hot response indexes apply
	 * and missing counts are 0.
	 */
	private static Specification<Ref> isPluginValue(PluginValueFilter filter, boolean before) {
		if (filter == null) return unrestricted();
		if (filter.property().startsWith("metadata->")) return (root, query, cb) -> {
			var count = cb.coalesce(
				cb.function("safe_numeric", Double.class,
					cb.function("jsonb_object_field", Object.class,
						cb.function("jsonb_object_field", Object.class,
							root.get(Ref_.metadata),
							cb.literal(filter.property().startsWith("metadata->remotePlugins->") ? "remotePlugins" : "plugins")),
						cb.literal(filter.plugin()))),
				cb.literal(0.0));
			var value = cb.function("cast_to_numeric", Double.class, cb.literal(filter.value()));
			return before ? cb.lessThan(count, value) : cb.greaterThan(count, value);
		};
		return (root, query, cb) -> compareJsonbValue(cb, createJsonbValue(root, cb, filter.property(), "plugins"), filter.value(), before);
	}

	/**
	 * Creates a Specification with sorting applied based on the PageRequest's sort orders.
	 * JSONB field sort columns are rewritten as JPA Specification orderBy clauses.
	 * Sort columns that target JSONB fields use the pattern "metadata->plugins->{pluginTag}"
	 * or generic JSONB paths like "metadata->field->subfield".
	 *
	 * @param spec the base specification to add sorting to
	 * @param pageable the page request containing sort orders
	 * @return a new Specification with sorting applied for all fields
	 */
	public static Specification<Ref> sort(Specification<Ref> spec, Pageable pageable) {
		if (pageable == null || pageable.getSort().isUnsorted()) {
			return spec;
		}
		// Collect all sort orders to apply in a single specification
		var orders = pageable.getSort().toList();
		return spec.and((root, query, cb) -> {
			if (query.getResultType() == Long.class || query.getResultType() == long.class) {
				return null; // Don't apply ordering to count queries
			}
			var jpaOrders = new ArrayList<Order>();
			for (Sort.Order order : orders) {
				var property = order.getProperty();
				var ascending = order.isAscending();
				Expression<?> expr;
				if (property.startsWith("plugins->plugin/user/vote:")) {
					// Handle vote sorting patterns using registered functions
					var voteType = property.substring("plugins->plugin/user/vote:".length());
					if ("top".equals(voteType)) {
						expr = cb.coalesce(cb.function("vote_top", Integer.class, root.get(Ref_.metadata)), cb.literal(0));
					} else if ("score".equals(voteType)) {
						expr = cb.coalesce(cb.function("vote_score", Integer.class, root.get(Ref_.metadata)), cb.literal(0));
					} else if ("decay".equals(voteType)) {
						expr = cb.coalesce(cb.function("vote_decay", Double.class, root.get(Ref_.metadata), root.get(Ref_.published)), cb.literal(0.0));
					} else {
						expr = null;
					}
				} else if (property.startsWith("tags->")) {
					expr = SortSpec.createTagValueSortExpression(root, cb, property);
				} else if (property.startsWith("metadata->")) {
					// Generated metadata: dates are missing until the first response, counts are never missing (0)
					var path = property.substring("metadata->".length()).replaceFirst(":(?:num|len)$", "").split("->");
					if (path.length == 1 && (path[0].equals("newResponse") || path[0].equals("newReaction"))) {
						var date = cb.function("jsonb_object_field_text", String.class,
							root.get(Ref_.metadata),
							cb.literal(path[0]));
						jpaOrders.add(ascending ? cb.asc(date, Nulls.FIRST) : cb.desc(date, Nulls.LAST));
						continue;
					} else if (path.length == 1 && (path[0].equals("expandedTags") || path[0].equals("responses") || path[0].equals("internalResponses"))) {
						expr = cb.coalesce(
							cb.function("safe_jsonb_array_length", Integer.class,
								cb.function("jsonb_object_field", Object.class,
									root.get(Ref_.metadata),
									cb.literal(path[0]))),
							cb.literal(0));
					} else if (path.length == 2 && (path[0].equals("plugins") || path[0].equals("remotePlugins"))) {
						expr = cb.coalesce(
							cb.function("safe_numeric", Double.class,
								cb.function("jsonb_object_field", Object.class,
									cb.function("jsonb_object_field", Object.class,
										root.get(Ref_.metadata),
										cb.literal(path[0])),
									cb.literal(path[1]))),
							cb.literal(0.0));
					} else {
						expr = null;
					}
				} else if (isJsonbSortProperty(property, "plugins")) {
					var jsonbOrder = createJsonbOrder(root, cb, property, ascending, "plugins");
					if (jsonbOrder != null) jpaOrders.add(jsonbOrder);
					continue;
				} else if (property.endsWith(":len")) {
					var fieldName = property.substring(0, property.length() - ":len".length());
					if ("origin".equals(fieldName)) {
						expr = cb.function("origin_nesting", Integer.class, root.get(fieldName));
					} else {
						expr = SortSpec.createArrayLengthExpression(root, cb, fieldName);
					}
				} else {
					expr = root.get(property);
				}
				if (expr != null) jpaOrders.add(ascending ? cb.asc(expr) : cb.desc(expr));
			}
			if (!jpaOrders.isEmpty()) query.orderBy(jpaOrders);
			return null;
		});
	}
}
