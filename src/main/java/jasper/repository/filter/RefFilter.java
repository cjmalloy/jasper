package jasper.repository.filter;

import jasper.domain.Ref;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.endsWithTitle;
import static jasper.repository.spec.RefSpec.fulltextEn;
import static jasper.repository.spec.RefSpec.hasInternalResponse;
import static jasper.repository.spec.RefSpec.hasNoPluginResponses;
import static jasper.repository.spec.RefSpec.hasNoResponses;
import static jasper.repository.spec.RefSpec.hasNoSources;
import static jasper.repository.spec.RefSpec.hasNoTags;
import static jasper.repository.spec.RefSpec.hasPluginResponses;
import static jasper.repository.spec.RefSpec.hasResponse;
import static jasper.repository.spec.RefSpec.hasSource;
import static jasper.repository.spec.RefSpec.isCreatedAfter;
import static jasper.repository.spec.RefSpec.isCreatedBefore;
import static jasper.repository.spec.RefSpec.isPublishedAfter;
import static jasper.repository.spec.RefSpec.isPublishedBefore;
import static jasper.repository.spec.RefSpec.isScheme;
import static jasper.repository.spec.RefSpec.isUrl;
import static jasper.repository.spec.ReplicationSpec.isModifiedAfter;
import static jasper.repository.spec.ReplicationSpec.isModifiedBefore;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Builder
@Getter
public class RefFilter implements Query {
	public static final String QUERY = Query.REGEX;
	private static final Logger logger = LoggerFactory.getLogger(RefFilter.class);

	private String origin;
	private String url;
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
	private Instant modifiedBefore;
	private Instant modifiedAfter;
	private Instant publishedBefore;
	private Instant publishedAfter;
	private Instant createdBefore;
	private Instant createdAfter;

	public Specification<Ref> spec() {
		var result = Specification.<Ref>where(null);
		if (origin != null && !origin.equals("@*")) {
			result = result.and(isOrigin(origin));
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
			// TODO: query across origins
			result = result.and(hasResponse(sources)
				.or(hasInternalResponse(sources)));
		}
		if (isNotBlank(responses)) {
			// TODO: query across origins
			result = result.and(hasSource(responses));
		}
		if (untagged) {
			result = result.and(hasNoTags());
		}
		if (uncited) {
			// TODO: query across origins
			result = result.and(hasNoResponses());
		}
		if (unsourced) {
			// TODO: query across origins
			result = result.and(hasNoSources());
		}
		if (pluginResponse != null) {
			// TODO: query across origins
			for (var r : pluginResponse) {
				result = result.and(hasPluginResponses(r));
			}
		}
		if (noPluginResponse != null) {
			// TODO: query across origins
			for (var nr : noPluginResponse) {
				result = result.and(hasNoPluginResponses(nr));
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
		return result;
	}
}
