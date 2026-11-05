package jasper.repository.filter;

import jasper.domain.proj.Tag;
import jasper.repository.spec.SortSpec.Filter;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static jasper.repository.spec.OriginSpec.isNesting;
import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.OriginSpec.none;
import static jasper.repository.spec.ReplicationSpec.isModifiedAfter;
import static jasper.repository.spec.SortSpec.filterTags;
import static jasper.repository.spec.TagSpec.isLevel;
import static jasper.repository.spec.TagSpec.searchTagOrName;
import static jasper.repository.spec.TagSpec.tagEndsWith;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.jpa.domain.Specification.not;

@Builder
@Getter
public class TagFilter implements Query {
	public static final String QUERY = Query.REGEX;
	public static final String FILTER = Filter.REGEX;
	public static final int FILTER_LEN = Filter.LEN;
	private static final Logger logger = LoggerFactory.getLogger(TagFilter.class);

	private String origin;
	private Integer nesting;
	private String query;
	private Integer level;
	private Boolean deleted;
	private String search;
	private Instant modifiedAfter;
	private List<String> filterBefore;
	private List<String> filterAfter;
	private List<String> filterEquals;

	/**
	 * @param roots the JSONB columns of the entity, the same columns it can be sorted by, e.g. "config"
	 */
	public <T extends Tag> Specification<T> spec(String... roots) {
		if ("!@*".equals(query)) return none();
		var result = Specification.<T>unrestricted();
		if (origin != null && !origin.equals("@*")) {
			result = result.and(isOrigin(origin));
		}
		if (isNotBlank(query) && !query.equals("@*")) {
			result = result.and(new TagQuery(query).spec());
		}
		if (nesting != null) {
			result = result.and(isNesting(nesting));
		}
		if (level != null) {
			result = result.and(isLevel(level));
		}
		if (deleted == null || !deleted) {
			result = result.and(not(tagEndsWith("deleted")));
		}
		if (isNotBlank(search)) {
			result = result.and(searchTagOrName(search));
		}
		if (modifiedAfter != null) {
			result = result.and(isModifiedAfter(modifiedAfter));
		}
		for (var f : filters()) {
			result = result.and(filterTags(f, roots));
		}
		return result;
	}

	/**
	 * The parsed filterBefore, filterAfter and filterEquals.
	 */
	public List<Filter> filters() {
		return Filter.parse(filterBefore, filterAfter, filterEquals);
	}

	public String cacheKey(Pageable pageable) {
		return
			pageable.getPageNumber() + "_" +
			pageable.getPageSize() + "_" +
			pageable.getSort() + "_" +
			origin + "_" +
			query + "_" +
			deleted + "_" +
			modifiedAfter + "_" +
			filterBefore + "_" +
			filterAfter + "_" +
			filterEquals + "_" +
			search;
	}
}
