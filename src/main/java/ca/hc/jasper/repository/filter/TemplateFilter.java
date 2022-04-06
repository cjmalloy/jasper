package ca.hc.jasper.repository.filter;

import static ca.hc.jasper.repository.spec.ReplicationSpec.isModifiedAfter;

import java.time.Instant;

import ca.hc.jasper.domain.Template;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

@Builder
@Getter
public class TemplateFilter implements Query {
	public static final String QUERY = Query.REGEX;
	private static final Logger logger = LoggerFactory.getLogger(TemplateFilter.class);

	private String query;
	private Instant modifiedAfter;

	public Specification<Template> spec() {
		var result = Specification.<Template>where(null);
		if (modifiedAfter != null) {
			result = result.and(isModifiedAfter(modifiedAfter));
		}
		if (query != null) {
			result = result.and(new TagQuery(query).templateSpec());
		}
		return result;
	}
}
