package ca.hc.jasper.repository.filter;

import static ca.hc.jasper.repository.spec.RefSpec.*;
import static ca.hc.jasper.repository.spec.ReplicationSpec.isModifiedAfter;

import java.time.Instant;
import java.util.List;

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
	private String sources;
	private String responses;
	private boolean uncited;
	private boolean unsourced;
	private Instant modifiedAfter;

	public interface GetSources { List<String> getSources(String url);}

	public Specification<Ref> spec() {
		return spec(null);
	}

	public Specification<Ref> spec(GetSources getSources) {
		var result = Specification.<Ref>where(null);
		if (query != null) {
			result = result.and(new TagQuery(query).refSpec());
		}
		if (sources != null) {
			// TODO: subquery
			result = result.and(isUrls(getSources.getSources(sources)));
		}
		if (responses != null) {
			result = result.and(hasSource(responses));
		}
		if (uncited) {
			// TODO: pre-processing
			throw new UnsupportedOperationException();
		}
		if (unsourced) {
			result = result.and(hasNoSources());
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
