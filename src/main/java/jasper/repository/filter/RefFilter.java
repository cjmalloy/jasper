package jasper.repository.filter;

import jasper.domain.Ref;
import jasper.repository.spec.SortSpec.Filter;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static jasper.repository.spec.OriginSpec.isNesting;
import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.OriginSpec.none;
import static jasper.repository.spec.RefSpec.*;
import static jasper.repository.spec.ReplicationSpec.isModifiedAfter;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.jpa.domain.Specification.not;

@Builder
@Getter
public class RefFilter implements Query {
	public static final String QUERY = Query.REGEX;
	public static final String FILTER = Filter.REGEX;
	public static final int FILTER_LEN = Filter.LEN;
	private static final Logger logger = LoggerFactory.getLogger(RefFilter.class);

	private String origin;
	private Integer nesting;
	private String url;
	private Boolean obsolete;
	private Boolean ignored;
	private String scheme;
	private String query;
	private String search;
	private String endsTitle;
	private boolean rankedOrder;
	private String sources;
	private String responses;
	private String user;
	private String userOrigin;
	private List<String> userResponse;
	private List<String> noUserResponse;
	private Instant modifiedAfter;
	private List<String> filter;

	public Specification<Ref> spec(String user, String userOrigin) {
		this.user = user;
		this.userOrigin = userOrigin;
		return spec();
	}

	public Specification<Ref> spec() {
		if ("!@*".equals(query)) return none();
		var result = Specification.<Ref>unrestricted();
		if (origin != null && !origin.equals("@*")) {
			result = result.and(isOrigin(origin));
		}
		if (obsolete != null) {
			if (!obsolete) {
				result = result.and(isNotObsolete());
			} else {
				result = result.and(not(isNotObsolete()));
			}
		}
		if (ignored != null) {
			if (!ignored) {
				result = result.and(isNotIgnored());
			} else {
				result = result.and(not(isNotIgnored()));
			}
		}
		if (nesting != null) {
			result = result.and(isNesting(nesting));
		}
		if (isNotBlank(url)) {
			result = result.and(isUrl(url));
		}
		if (isNotBlank(scheme)) {
			result = result.and(isScheme(scheme));
		}
		if (isNotBlank(query)) {
			result = result.and(new TagQuery(query).refSpec());
		}
		if (isNotBlank(search)) {
			result = result.and(isUrl(search).or(fulltextEn(search, rankedOrder)));
		}
		if (isNotBlank(endsTitle)) {
			result = result.and(endsWithTitle(endsTitle));
		}
		if (isNotBlank(sources)) {
			result = result.and(hasResponse(sources)
				.or(hasInternalResponse(sources)));
		}
		if (isNotBlank(responses)) {
			result = result.and(hasSource(responses));
		}
		if (isNotBlank(user)) {
			if (userResponse != null) {
				for (var r : userResponse) {
					result = result.and(hasPluginResponses(user, userOrigin, r));
				}
			}
			if (noUserResponse != null) {
				for (var nr : noUserResponse) {
					result = result.and(hasNoPluginResponses(user, userOrigin, nr));
				}
			}
		}
		if (modifiedAfter != null) {
			result = result.and(isModifiedAfter(modifiedAfter));
		}
		for (var f : filters()) {
			result = result.and(filter(f));
		}
		return result;
	}

	/**
	 * The parsed filters.
	 */
	public List<Filter> filters() {
		return Filter.parse(filter);
	}
}
