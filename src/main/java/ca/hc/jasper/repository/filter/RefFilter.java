package ca.hc.jasper.repository.filter;

import static ca.hc.jasper.repository.spec.RefSpec.*;
import static ca.hc.jasper.repository.spec.ReplicationSpec.isModifiedAfter;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.time.Instant;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.domain.proj.HasTags;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

@Builder
@Getter
public class RefFilter implements Query {
	public static final String QUERY = Query.REGEX;
	private static final Logger logger = LoggerFactory.getLogger(RefFilter.class);

	private String query;
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
		if (isNotBlank(query)) {
			result = result.and(new TagQuery(query).refSpec());
		}
		if (isNotBlank(search)) {
			result = result.and(fulltextEn(search, rankedOrder));
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
