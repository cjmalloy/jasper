package ca.hc.jasper.repository.filter;

import static ca.hc.jasper.repository.spec.ReplicationSpec.isModifiedAfter;

import java.time.Instant;

import ca.hc.jasper.domain.proj.HasTags;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

@Builder
public class RefFilter {
	public static final String QUERY = TagQuery.REGEX;
	private static final Logger logger = LoggerFactory.getLogger(RefFilter.class);

	private String query;
	private Instant modifiedAfter;

	public <T extends HasTags> Specification<T> spec() {
		var result = Specification.<T>where(null);
		if (modifiedAfter != null) {
			result = result.and(isModifiedAfter(modifiedAfter));
		}
		if (query != null) {
			result = result.and(new TagQuery(query).refSpec());
		}
		return result;
	}
}
