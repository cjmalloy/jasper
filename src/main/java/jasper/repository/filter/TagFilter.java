package jasper.repository.filter;

import jasper.domain.proj.Tag;
import jasper.errors.InvalidQueryException;
import jasper.repository.spec.SortSpec.ConfigValueFilter;
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
import static jasper.repository.spec.ReplicationSpec.isModifiedBefore;
import static jasper.repository.spec.SortSpec.isConfigValue;
import static jasper.repository.spec.TagSpec.isLevel;
import static jasper.repository.spec.TagSpec.searchTagOrName;
import static jasper.repository.spec.TagSpec.tagEndsWith;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.jpa.domain.Specification.not;

@Builder
@Getter
public class TagFilter implements Query {
	public static final String QUERY = Query.REGEX;
	public static final String CONFIG_VALUE = ConfigValueFilter.REGEX;
	public static final int CONFIG_VALUE_LEN = ConfigValueFilter.LEN;
	private static final Logger logger = LoggerFactory.getLogger(TagFilter.class);

	private String origin;
	private Integer nesting;
	private String query;
	private Integer level;
	private Boolean deleted;
	private String search;
	private Instant modifiedBefore;
	private Instant modifiedAfter;
	private List<String> configBefore;
	private List<String> configAfter;

	/**
	 * @param configRoots the JSONB columns configBefore and configAfter may filter on,
	 *                    the same columns the entity can be sorted by, e.g. "config"
	 */
	public <T extends Tag> Specification<T> spec(String... configRoots) {
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
		if (modifiedBefore != null) {
			result = result.and(isModifiedBefore(modifiedBefore));
		}
		if (modifiedAfter != null) {
			result = result.and(isModifiedAfter(modifiedAfter));
		}
		if (configBefore != null) {
			for (var f : configBefore) {
				result = result.and(isConfigValue(configValueFilter(f, configRoots), true));
			}
		}
		if (configAfter != null) {
			for (var f : configAfter) {
				result = result.and(isConfigValue(configValueFilter(f, configRoots), false));
			}
		}
		return result;
	}

	public boolean hasConfigFilter() {
		return configBefore != null && !configBefore.isEmpty()
			|| configAfter != null && !configAfter.isEmpty();
	}

	private static ConfigValueFilter configValueFilter(String filter, String... roots) {
		var result = ConfigValueFilter.parse(filter, roots);
		if (result == null) throw new InvalidQueryException(filter, "expected {field}->{path}/{value}, {field}->{path}:num/{number} or {field}->{path}:len/{length} with {field} one of " + String.join(", ", roots));
		return result;
	}

	public String cacheKey(Pageable pageable) {
		return
			pageable.getPageNumber() + "_" +
			pageable.getPageSize() + "_" +
			pageable.getSort() + "_" +
			origin + "_" +
			query + "_" +
			deleted + "_" +
			modifiedBefore + "_" +
			modifiedAfter + "_" +
			configBefore + "_" +
			configAfter + "_" +
			search;
	}
}
