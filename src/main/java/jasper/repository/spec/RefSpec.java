package jasper.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static jasper.domain.proj.Tag.isPublicTag;
import static jasper.domain.proj.Tag.publicTag;
import static jasper.repository.spec.OriginSpec.none;
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
					cb.literal(Ref_.MODIFIED)),
				cb.literal(i.toString()));
	}

	public static Specification<Ref> isResponseBefore(Instant i) {
		if (i == null) return unrestricted();
		return (root, query, cb) ->
			cb.lessThan(cb.function("jsonb_object_field_text", String.class,
					root.get(Ref_.metadata),
					cb.literal(Ref_.MODIFIED)),
				cb.literal(i.toString()));
	}

	/**
	 * Creates a specification that adds ordering for a JSONB metadata plugins field.
	 * The path format is "metadata->plugins->{pluginTag}" where pluginTag is the plugin identifier.
	 * Example: "metadata->plugins->plugin/comment" sorts by the comment count.
	 *
	 * @param pluginTag the plugin tag to sort by (e.g., "plugin/comment")
	 * @param ascending true for ascending order, false for descending
	 * @return a specification that applies the ordering
	 */
	public static Specification<Ref> orderByPluginCount(String pluginTag, boolean ascending) {
		if (pluginTag == null || pluginTag.isBlank()) return unrestricted();
		return (root, query, cb) -> {
			if (query.getResultType() == Long.class || query.getResultType() == long.class) {
				return null; // Don't apply ordering to count queries
			}
			var expr = cb.coalesce(
				cb.function("cast_to_int", Integer.class,
					cb.function("jsonb_object_field_text", String.class,
						cb.function("jsonb_object_field", Object.class,
							root.get(Ref_.metadata),
							cb.literal("plugins")),
						cb.literal(pluginTag))),
				cb.literal(0));
			if (ascending) {
				query.orderBy(cb.asc(expr));
			} else {
				query.orderBy(cb.desc(expr));
			}
			return null; // No predicate filter, just ordering
		};
	}

	/**
	 * Creates a specification that adds ordering based on a jsonb path expression.
	 * Supports arbitrary JSONB field access for sorting.
	 * Append ":num" to the last field for numeric sorting (e.g., ["metadata", "count:num"]).
	 *
	 * @param jsonbPath the JSONB path to sort by (e.g., ["metadata", "plugins", "plugin/comment"])
	 * @param ascending true for ascending order, false for descending
	 * @return a specification that applies the ordering
	 */
	public static Specification<Ref> orderByJsonbPath(List<String> jsonbPath, boolean ascending) {
		if (jsonbPath == null || jsonbPath.isEmpty()) return unrestricted();
		return (root, query, cb) -> {
			if (query.getResultType() == Long.class || query.getResultType() == long.class) {
				return null; // Don't apply ordering to count queries
			}
			// Check if numeric sorting is requested
			var lastField = jsonbPath.get(jsonbPath.size() - 1);
			var numericSort = lastField.endsWith(":num");
			if (numericSort) {
				lastField = lastField.substring(0, lastField.length() - 4);
			}
			Expression<?> expr = root.get(jsonbPath.get(0));
			for (int i = 1; i < jsonbPath.size() - 1; i++) {
				expr = cb.function("jsonb_object_field_text", String.class,
					expr,
					cb.literal(jsonbPath.get(i)));
			}
			if (jsonbPath.size() > 1) {
				expr = cb.function("jsonb_object_field_text", String.class,
					expr,
					cb.literal(lastField));
			}
			// Cast to numeric if requested
			if (numericSort) {
				expr = cb.function("cast_to_numeric", Double.class, expr);
			}
			if (ascending) {
				query.orderBy(cb.asc(expr));
			} else {
				query.orderBy(cb.desc(expr));
			}
			return null; // No predicate filter, just ordering
		};
	}

	/**
	 * Creates a specification that adds ordering based on a field within a plugin's data.
	 * The path format is "plugins->{pluginTag}->{field}" where field can be nested.
	 * Append ":num" to the last field for numeric sorting (e.g., "plugins->_plugin/cache->contentLength:num").
	 * Example: "plugins->_plugin/cache->contentLength" sorts by the contentLength field.
	 *
	 * @param pluginTag the plugin tag (e.g., "_plugin/cache")
	 * @param fieldPath the path to the field within the plugin data
	 * @param ascending true for ascending order, false for descending
	 * @return a specification that applies the ordering
	 */
	public static Specification<Ref> orderByPluginField(String pluginTag, List<String> fieldPath, boolean ascending) {
		if (pluginTag == null || pluginTag.isBlank() || fieldPath == null || fieldPath.isEmpty()) return unrestricted();
		return (root, query, cb) -> {
			if (query.getResultType() == Long.class || query.getResultType() == long.class) {
				return null; // Don't apply ordering to count queries
			}
			// Check if numeric sorting is requested
			var lastField = fieldPath.get(fieldPath.size() - 1);
			var numericSort = lastField.endsWith(":num");
			if (numericSort) {
				lastField = lastField.substring(0, lastField.length() - 4);
			}
			// Start with plugins->{pluginTag}
			Expression<?> expr = cb.function("jsonb_object_field", Object.class,
				root.get(Ref_.plugins),
				cb.literal(pluginTag));
			// Navigate through field path
			for (int i = 0; i < fieldPath.size() - 1; i++) {
				expr = cb.function("jsonb_object_field", Object.class,
					expr,
					cb.literal(fieldPath.get(i)));
			}
			// Get the final field as text
			expr = cb.function("jsonb_object_field_text", String.class,
				expr,
				cb.literal(lastField));
			// Cast to numeric if requested
			if (numericSort) {
				expr = cb.function("cast_to_numeric", Double.class, expr);
			}
			if (ascending) {
				query.orderBy(cb.asc(expr));
			} else {
				query.orderBy(cb.desc(expr));
			}
			return null; // No predicate filter, just ordering
		};
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
	public static Specification<Ref> applySortingSpec(Specification<Ref> spec, Pageable pageable) {
		if (pageable == null || pageable.getSort().isUnsorted()) {
			return spec;
		}
		// Collect all sort orders to apply in a single specification
		var orders = pageable.getSort().toList();
		return spec.and((root, query, cb) -> {
			if (query.getResultType() == Long.class || query.getResultType() == long.class) {
				return null; // Don't apply ordering to count queries
			}
			var jpaOrders = new java.util.ArrayList<jakarta.persistence.criteria.Order>();
			for (Sort.Order order : orders) {
				var property = order.getProperty();
				var ascending = order.isAscending();
				if (property == null) continue;

				Expression<?> expr;
				boolean isJsonbField = property.startsWith("metadata->") || property.startsWith("plugins->");
				boolean isLengthSort = property.endsWith(":len");
				boolean isVoteSort = property.startsWith("plugins->plugin/user/vote:");
				if (isVoteSort) {
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
				} else if (isJsonbField) {
					expr = createJsonbSortExpression(root, cb, property);
				} else if (isLengthSort) {
					// Handle direct JSONB array field length sorting (e.g., "tags:len", "sources:len")
					// or origin nesting level ("origin:len")
					var fieldName = property.substring(0, property.length() - 4);
					if ("origin".equals(fieldName)) {
						// Special case: origin:len calculates nesting level by splitting on '.'
						expr = cb.function("origin_nesting", Integer.class, root.get(fieldName));
					} else {
						expr = cb.coalesce(cb.function("jsonb_array_length", Integer.class, root.get(fieldName)), cb.literal(0));
					}
				} else {
					expr = root.get(property);
				}
				if (expr != null) {
					jpaOrders.add(ascending ? cb.asc(expr) : cb.desc(expr));
				}
			}
			if (!jpaOrders.isEmpty()) {
				query.orderBy(jpaOrders);
			}
			return null;
		});
	}

	/**
	 * Creates a JSONB sort expression for the given property path.
	 * Supports ":num" suffix for numeric sorting and ":len" suffix for array/object length sorting.
	 * Uses COALESCE to handle nulls (0 for numeric/length, '' for string).
	 */
	private static Expression<?> createJsonbSortExpression(Root<Ref> root, CriteriaBuilder cb, String property) {
		var parts = property.split("->");
		if (parts.length < 2) {
			return null;
		}

		// Check if numeric or length sorting is requested
		var lastPart = parts[parts.length - 1];
		var numericSort = lastPart.endsWith(":num");
		var lengthSort = lastPart.endsWith(":len");
		if (numericSort) {
			parts[parts.length - 1] = lastPart.substring(0, lastPart.length() - 4);
		} else if (lengthSort) {
			parts[parts.length - 1] = lastPart.substring(0, lastPart.length() - 4);
		}

		// Handle "metadata->plugins->{pluginTag}" pattern - sorting by response count
		if (parts.length >= 3 && "metadata".equals(parts[0]) && "plugins".equals(parts[1])) {
			var pluginTag = parts[2];
			Expression<?> expr = cb.function("jsonb_object_field", Object.class,
				root.get(Ref_.metadata),
				cb.literal("plugins"));
			expr = cb.function("jsonb_object_field_text", String.class,
				expr,
				cb.literal(pluginTag));
			return cb.coalesce(cb.function("cast_to_int", Integer.class, expr), cb.literal(0));
		}

		// Handle "plugins->{pluginTag}->{field}" pattern - sorting by plugin field value
		if (parts.length >= 3 && "plugins".equals(parts[0])) {
			var pluginTag = parts[1];
			Expression<?> expr = cb.function("jsonb_object_field", Object.class,
				root.get(Ref_.plugins),
				cb.literal(pluginTag));
			// Navigate through field path
			for (int i = 2; i < parts.length - 1; i++) {
				expr = cb.function("jsonb_object_field", Object.class,
					expr,
					cb.literal(parts[i]));
			}
			// Get the final field - as JSONB for length, as text otherwise
			if (lengthSort) {
				expr = cb.function("jsonb_object_field", Object.class,
					expr,
					cb.literal(parts[parts.length - 1]));
				return cb.coalesce(cb.function("jsonb_array_length", Integer.class, expr), cb.literal(0));
			} else {
				expr = cb.function("jsonb_object_field_text", String.class,
					expr,
					cb.literal(parts[parts.length - 1]));
				if (numericSort) {
					return cb.coalesce(cb.function("cast_to_numeric", Double.class, expr), cb.literal(0.0));
				}
				return cb.coalesce(expr, cb.literal(""));
			}
		}

		// Handle generic JSONB path (metadata->field->subfield)
		if ("metadata".equals(parts[0])) {
			Expression<?> expr = root.get(Ref_.metadata);
			for (int i = 1; i < parts.length - 1; i++) {
				expr = cb.function("jsonb_object_field", Object.class,
					expr,
					cb.literal(parts[i]));
			}
			// Get the final field - as JSONB for length, as text otherwise
			if (lengthSort) {
				expr = cb.function("jsonb_object_field", Object.class,
					expr,
					cb.literal(parts[parts.length - 1]));
				return cb.coalesce(cb.function("jsonb_array_length", Integer.class, expr), cb.literal(0));
			} else {
				expr = cb.function("jsonb_object_field_text", String.class,
					expr,
					cb.literal(parts[parts.length - 1]));
				if (numericSort) {
					return cb.coalesce(cb.function("cast_to_numeric", Double.class, expr), cb.literal(0.0));
				}
				return cb.coalesce(expr, cb.literal(""));
			}
		}

		return null;
	}
}
