package jasper.repository.filter;

import jasper.domain.Ref;
import jasper.domain.proj.HasTags;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.fulltextEn;
import static jasper.repository.spec.RefSpec.hasInternalResponse;
import static jasper.repository.spec.RefSpec.hasNoPluginResponses;
import static jasper.repository.spec.RefSpec.hasNoResponses;
import static jasper.repository.spec.RefSpec.hasNoSources;
import static jasper.repository.spec.RefSpec.hasPluginResponses;
import static jasper.repository.spec.RefSpec.hasResponse;
import static jasper.repository.spec.RefSpec.hasSource;
import static jasper.repository.spec.RefSpec.isUrl;
import static jasper.repository.spec.ReplicationSpec.isModifiedAfter;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Builder
@Getter
public class RefFilter implements Query {
	public static final String QUERY = Query.REGEX;
	private static final Logger logger = LoggerFactory.getLogger(RefFilter.class);

	private String url;
	private String query;
	private boolean local;
	private String search;
	private boolean rankedOrder;
	private String sources;
	private String responses;
	private boolean uncited;
	private boolean unsourced;
	private String pluginResponse;
	private String noPluginResponse;
	private Instant modifiedAfter;

	public Specification<Ref> spec() {
		var result = Specification.<Ref>where(null);
		if (isNotBlank(url)) {
			result = result.and(isUrl(url));
		}
		if (isNotBlank(query)) {
			result = result.and(new TagQuery(query).refSpec());
		}
		if (local) {
			result = result.and(isOrigin(""));
		}
		if (isNotBlank(search)) {
			result = result.and(isUrl(search).or(fulltextEn(search, rankedOrder)));
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
		if (uncited) {
			// TODO: query across origins
			result = result.and(hasNoResponses());
		}
		if (unsourced) {
			// TODO: query across origins
			result = result.and(hasNoSources());
		}
		if (isNotBlank(pluginResponse)) {
			// TODO: query across origins
			result = result.and(hasPluginResponses(pluginResponse));
		}
		if (isNotBlank(noPluginResponse)) {
			// TODO: query across origins
			result = result.and(hasNoPluginResponses(pluginResponse));
		}
		if (modifiedAfter != null) {
			result = result.and(isModifiedAfter(modifiedAfter));
		}
		return result;
	}

	public <T extends HasTags> Specification<T> feedSpec() {
		var result = Specification.<T>where(null);
		if (query != null) {
			result = result.and(new TagQuery(query).refSpec());
		}
		if (local) {
			result = result.and(isOrigin(""));
		}
		if (sources != null) {
			throw new UnsupportedOperationException();
		}
		if (responses != null) {
			throw new UnsupportedOperationException();
		}
		if (uncited) {
			throw new UnsupportedOperationException();
		}
		if (unsourced) {
			throw new UnsupportedOperationException();
		}
		if (modifiedAfter != null) {
			result = result.and(isModifiedAfter(modifiedAfter));
		}
		return result;
	}
}
