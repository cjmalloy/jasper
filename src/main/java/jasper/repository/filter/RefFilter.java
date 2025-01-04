package jasper.repository.filter;

import jasper.domain.Ref;
import jasper.repository.spec.QualifiedTag;
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
import static jasper.repository.spec.ReplicationSpec.isModifiedBefore;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Builder
@Getter
public class RefFilter implements Query {
	public static final String QUERY = Query.REGEX;
	private static final Logger logger = LoggerFactory.getLogger(RefFilter.class);

	private String origin;
	private Integer nesting;
	private String url;
	private boolean obsolete;
	private String scheme;
	private String query;
	private String search;
	private String endsTitle;
	private boolean rankedOrder;
	private String sources;
	private String responses;
	private boolean untagged;
	private boolean uncited;
	private boolean unsourced;
	private List<String> pluginResponse;
	private List<String> noPluginResponse;
	private String user;
	private List<String> userResponse;
	private List<String> noUserResponse;
	private Instant modifiedBefore;
	private Instant modifiedAfter;
	private Instant publishedBefore;
	private Instant publishedAfter;
	private Instant createdBefore;
	private Instant createdAfter;
	private Instant responseBefore;
	private Instant responseAfter;

	public Specification<Ref> spec(QualifiedTag user) {
		if (user != null) this.user = user.tag;
		return spec();
	}

	public Specification<Ref> spec() {
		if ("!@*".equals(query)) return none();
		var result = Specification.<Ref>where(null);
		if (origin != null && !origin.equals("@*")) {
			result = result
				.and(isOrigin(origin))
				.and(isNotObsolete());
		if (nesting != null) {
			result = result.and(isNesting(nesting));
		}
		} else if (!obsolete) {
			result = result.and(isNotObsolete());
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
		if (untagged) {
			result = result.and(hasNoTags());
		}
		if (uncited) {
			result = result.and(hasNoResponses());
		}
		if (unsourced) {
			result = result.and(hasNoSources());
		}
		if (pluginResponse != null) {
			for (var r : pluginResponse) {
				result = result.and(hasPluginResponses(r));
			}
		}
		if (noPluginResponse != null) {
			for (var nr : noPluginResponse) {
				result = result.and(hasNoPluginResponses(nr));
			}
		}
		if (isNotBlank(user)) {
			if (userResponse != null) {
				for (var r : userResponse) {
					result = result.and(hasPluginResponses(user, r));
				}
			}
			if (noUserResponse != null) {
				for (var nr : noUserResponse) {
					result = result.and(hasNoPluginResponses(user, nr));
				}
			}
		}
		if (modifiedBefore != null) {
			result = result.and(isModifiedBefore(modifiedBefore));
		}
		if (modifiedAfter != null) {
			result = result.and(isModifiedAfter(modifiedAfter));
		}
		if (publishedBefore != null) {
			result = result.and(isPublishedBefore(publishedBefore));
		}
		if (publishedAfter != null) {
			result = result.and(isPublishedAfter(publishedAfter));
		}
		if (createdBefore != null) {
			result = result.and(isCreatedBefore(createdBefore));
		}
		if (createdAfter != null) {
			result = result.and(isCreatedAfter(createdAfter));
		}
		if (responseBefore != null) {
			result = result.and(isResponseBefore(responseBefore));
		}
		if (responseAfter != null) {
			result = result.and(isResponseAfter(responseAfter));
		}
		return result;
	}
}
