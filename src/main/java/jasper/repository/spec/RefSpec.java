package jasper.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.errors.InvalidQueryException;
import jasper.repository.spec.SortSpec.Column;
import jasper.repository.spec.SortSpec.Filter;
import jasper.repository.spec.SortSpec.TagValueSort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

import static jasper.domain.proj.Tag.isPublicTag;
import static jasper.domain.proj.Tag.publicTag;
import static jasper.domain.proj.Tag.userUrlPrefix;
import static jasper.repository.spec.OriginSpec.none;
import static jasper.repository.spec.SortSpec.isJsonbSortProperty;
import static jasper.repository.spec.SortSpec.jsonbColumn;
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

	/**
	 * Filters on a sort column, using the same expression as sorting so the same indexes apply.
	 */
	public static Specification<Ref> filter(Filter filter) {
		return (root, query, cb) -> {
			var column = column(root, cb, filter.column());
			if (column == null) throw new InvalidQueryException(filter.column(), "not a sort column");
			return column.filter(cb, filter);
		};
	}

	/**
	 * The expression of a computed sort column, also used for filters, so one index serves both.
	 *
	 * @return the column, or null if this is not a computed column
	 */
	public static Column column(Root<Ref> root, CriteriaBuilder cb, String property) {
		if (property.startsWith("plugins->plugin/user/vote:")) {
			// Vote sorts use registered functions
			return switch (property.substring("plugins->plugin/user/vote:".length())) {
				case "top" -> new Column(cb.coalesce(cb.function("vote_top", Integer.class, root.get(Ref_.metadata)), cb.literal(0)), false);
				case "score" -> new Column(cb.coalesce(cb.function("vote_score", Integer.class, root.get(Ref_.metadata)), cb.literal(0)), false);
				case "decay" -> new Column(cb.coalesce(cb.function("vote_decay", Double.class, root.get(Ref_.metadata), root.get(Ref_.published)), cb.literal(0.0)), false);
				default -> null;
			};
		}
		if (property.startsWith("tags->")) {
			// Missing or invalid tag values are NULL
			var sort = TagValueSort.parse(property);
			if (sort == null) return null;
			if ("tag_value".equals(sort.function())) return new Column(cb.function("tag_value", String.class, root.get(Ref_.tags), cb.literal(sort.tag())), true);
			return new Column(cb.function(sort.function(), Double.class, root.get(Ref_.tags), cb.literal(sort.tag())), true);
		}
		if (property.startsWith("metadata->")) {
			// Generated metadata: dates are missing until the first response, counts are never missing (0)
			var path = property.substring("metadata->".length()).replaceFirst(":(?:num|len)$", "").split("->");
			if (path.length == 1 && (path[0].equals("newResponse") || path[0].equals("newReaction"))) {
				return new Column(cb.function("jsonb_object_field_text", String.class,
					root.get(Ref_.metadata),
					cb.literal(path[0])), true);
			}
			if (path.length == 1 && (path[0].equals("expandedTags") || path[0].equals("responses") || path[0].equals("internalResponses"))) {
				return new Column(cb.coalesce(
					cb.function("safe_jsonb_array_length", Integer.class,
						cb.function("jsonb_object_field", Object.class,
							root.get(Ref_.metadata),
							cb.literal(path[0]))),
					cb.literal(0)), false);
			}
			if (path.length == 2 && (path[0].equals("plugins") || path[0].equals("remotePlugins"))) {
				return new Column(cb.coalesce(
					cb.function("safe_numeric", Double.class,
						cb.function("jsonb_object_field", Object.class,
							cb.function("jsonb_object_field", Object.class,
								root.get(Ref_.metadata),
								cb.literal(path[0])),
							cb.literal(path[1]))),
					cb.literal(0.0)), false);
			}
			return null;
		}
		if (isJsonbSortProperty(property, "plugins")) return jsonbColumn(root, cb, property, "plugins");
		return switch (property) {
			case "modified" -> new Column(root.get(Ref_.modified), false);
			case "published" -> new Column(root.get(Ref_.published), false);
			case "created" -> new Column(root.get(Ref_.created), false);
			case "origin:len" -> new Column(cb.function("origin_nesting", Integer.class, root.get(Ref_.origin)), false);
			case "tags:len", "sources:len", "alternateUrls:len" -> new Column(cb.coalesce(
				cb.function("jsonb_array_length", Integer.class,
					root.get(property.substring(0, property.length() - ":len".length()))),
				cb.literal(0)), false);
			default -> null;
		};
	}

	/**
	 * Creates a Specification with sorting applied based on the PageRequest's sort orders.
	 * Computed sort columns use the same expression as filters (see {@link #column}).
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
				var column = column(root, cb, property);
				if (column != null) {
					jpaOrders.add(column.order(cb, order.isAscending()));
				} else if (!property.contains("->") && !property.contains(":")) {
					jpaOrders.add(order.isAscending() ? cb.asc(root.get(property)) : cb.desc(root.get(property)));
				} else if (!property.contains("->")) {
					throw new InvalidQueryException(property, "not a sort column");
				}
			}
			if (!jpaOrders.isEmpty()) query.orderBy(jpaOrders);
			return null;
		});
	}
}
