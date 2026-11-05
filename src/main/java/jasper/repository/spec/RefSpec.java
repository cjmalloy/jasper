package jasper.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
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
import static jasper.domain.proj.Tag.matchesTemplate;
import static jasper.domain.proj.Tag.publicTag;
import static jasper.repository.spec.OriginSpec.isUnderOrigin;
import static jasper.repository.spec.OriginSpec.none;
import static jasper.repository.spec.SortSpec.compareJsonbValue;
import static jasper.repository.spec.SortSpec.createJsonbSortExpression;
import static jasper.repository.spec.SortSpec.createJsonbValue;
import static jasper.repository.spec.SortSpec.isJsonbSortProperty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.data.jpa.domain.Specification.not;
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

	public static Specification<Ref> hasNoPluginResponses(String plugin) {
		return hasNoResponseCount("plugins", plugin);
	}

	public static Specification<Ref> hasPluginResponses(String plugin) {
		return hasResponseCount("plugins", plugin);
	}

	public static Specification<Ref> hasNoRemotePluginResponses(String plugin) {
		return hasNoResponseCount("remotePlugins", plugin);
	}

	public static Specification<Ref> hasRemotePluginResponses(String plugin) {
		return hasResponseCount("remotePlugins", plugin);
	}

	/**
	 * Compares the response count using the same expression as sorting by "metadata->{field}->{plugin}",
	 * so hot response indexes apply to both.
	 */
	@SuppressWarnings("unchecked")
	private static Specification<Ref> hasResponseCount(String field, String plugin) {
		return (root, query, cb) -> cb.greaterThan(
			(Expression<Double>) createJsonbSortExpression(root, cb, "metadata->" + field + "->" + plugin + ":num", "metadata"),
			cb.function("cast_to_numeric", Double.class, cb.literal(0)));
	}

	private static Specification<Ref> hasNoResponseCount(String field, String plugin) {
		return (root, query, cb) -> cb.equal(
			createJsonbSortExpression(root, cb, "metadata->" + field + "->" + plugin + ":num", "metadata"),
			cb.function("cast_to_numeric", Double.class, cb.literal(0)));
	}

	/**
	 * Refs the user has not responded to with a user URL Ref tagged with the plugin.
	 * See {@link #hasPluginResponses(String, String, String)}.
	 */
	public static Specification<Ref> hasNoPluginResponses(String user, String userOrigin, String plugin) {
		return not(hasPluginResponses(user, userOrigin, plugin));
	}

	/**
	 * Refs the user has responded to with a user URL Ref ("tag:/user/{name}?url={url}", or the
	 * "+user" and "_user" forms) in the user's origin, tagged with the plugin and citing the Ref as a source.
	 * This is what metadata userUrls records, but instead of checking the userUrls of every Ref
	 * (whose key depends on the Ref's own URL, so no index can help), this is a semi-join against the
	 * user's own user URL Refs. On PostgreSQL the URL prefix index (urlPrefix), the tags GIN index and the
	 * primary key serve it.
	 * Like metadata userUrls, only "plugin/user" plugins are recorded, and only Refs under the user's origin.
	 */
	public static Specification<Ref> hasPluginResponses(String user, String userOrigin, String plugin) {
		if (!matchesTemplate("plugin/user", plugin)) return none();
		var origin = isBlank(userOrigin) || userOrigin.equals("@") ? "" : userOrigin;
		var name = publicTag(user);
		return Specification.<Ref>where(isUnderOrigin(origin)).and((root, query, cb) -> {
			var sub = query.subquery(String.class);
			var u = sub.from(Ref.class);
			var url = cb.substring(u.get(Ref_.url), cb.sum(cb.locate(u.get(Ref_.url), cb.literal("?")), cb.literal(5)));
			sub.select(url).where(
				cb.or(
					cb.like(u.get(Ref_.url), cb.literal(likePrefix("tag:/" + name + "?url=")), '\\'),
					cb.like(u.get(Ref_.url), cb.literal(likePrefix("tag:/+" + name + "?url=")), '\\'),
					cb.like(u.get(Ref_.url), cb.literal(likePrefix("tag:/_" + name + "?url=")), '\\')),
				cb.equal(u.get(Ref_.origin), cb.literal(origin)),
				tagExists(u, cb, plugin),
				cb.isTrue(cb.function("jsonb_exists", Boolean.class, u.get(Ref_.sources), url)));
			return root.get(Ref_.url).in(sub);
		});
	}

	/**
	 * A LIKE pattern matching strings starting with the prefix, escaped with a backslash.
	 */
	private static String likePrefix(String prefix) {
		return prefix
			.replace("\\", "\\\\")
			.replace("%", "\\%")
			.replace("_", "\\_") + "%";
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

	public static Specification<Ref> hasNoChildTag(String tag) {
		return (root, query, cb) -> cb.isFalse(
			cb.like(
				cb.function("jsonb_text", String.class, root.get(Ref_.tags)),
				"%\"" + tag.replace("_", "\\_") + "/%",
				'\\'));
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

	/**
	 * Metadata modified (the last time responses, internal responses or plugin counts changed) after this time.
	 */
	public static Specification<Ref> isResponseAfter(Instant i) {
		if (i == null) return unrestricted();
		return isMetadataDate(Ref_.MODIFIED, i.toString(), false);
	}

	/**
	 * Metadata modified (the last time responses, internal responses or plugin counts changed) before this time.
	 */
	public static Specification<Ref> isResponseBefore(Instant i) {
		if (i == null) return unrestricted();
		return isMetadataDate(Ref_.MODIFIED, i.toString(), true);
	}

	public static Specification<Ref> isNewResponseAfter(Instant i) {
		if (i == null) return unrestricted();
		return isMetadataDate("newResponse", timestamp(i), false);
	}

	public static Specification<Ref> isNewResponseBefore(Instant i) {
		if (i == null) return unrestricted();
		return isMetadataDate("newResponse", timestamp(i), true);
	}

	public static Specification<Ref> isNewReactionAfter(Instant i) {
		if (i == null) return unrestricted();
		return isMetadataDate("newReaction", timestamp(i), false);
	}

	public static Specification<Ref> isNewReactionBefore(Instant i) {
		if (i == null) return unrestricted();
		return isMetadataDate("newReaction", timestamp(i), true);
	}

	/**
	 * Compares a metadata date as text, using the same expression as sorting by "metadata->{field}"
	 * (including the COALESCE), so the metadataModified, newResponse and newReaction indexes apply.
	 * Missing dates are '', so they never match: before also requires a value greater than ''.
	 *
	 * @param value the date in the same text format the metadata field is written in
	 */
	@SuppressWarnings("unchecked")
	private static Specification<Ref> isMetadataDate(String field, String value, boolean before) {
		return (root, query, cb) -> {
			var date = (Expression<String>) createJsonbSortExpression(root, cb, "metadata->" + field, "metadata");
			if (!before) return cb.greaterThan(date, cb.literal(value));
			return cb.and(
				cb.greaterThan(date, cb.literal("")),
				cb.lessThan(date, cb.literal(value)));
		};
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
	 * Compares a field of plugin data, using the same expression as plugin sorting.
	 * Refs without a value for the field never match.
	 * Response counts use the same expression as sorting by "metadata->plugins->{plugin}" and the
	 * pluginResponse filters, including the COALESCE, so hot response indexes apply and missing counts are 0.
	 */
	@SuppressWarnings("unchecked")
	private static Specification<Ref> isPluginValue(PluginValueFilter filter, boolean before) {
		if (filter == null) return unrestricted();
		if (filter.responseCount()) return (root, query, cb) -> {
			var count = (Expression<Double>) createJsonbSortExpression(root, cb, filter.property(), "metadata");
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
				} else if (isJsonbSortProperty(property, "metadata", "plugins")) {
					expr = createJsonbSortExpression(root, cb, property, "metadata", "plugins");
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
