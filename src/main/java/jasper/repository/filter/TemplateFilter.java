package jasper.repository.filter;

import jasper.domain.Template;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.ReplicationSpec.isModifiedAfter;
import static jasper.repository.spec.ReplicationSpec.isModifiedBefore;
import static jasper.repository.spec.TagSpec.searchTagOrName;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Builder
@Getter
public class TemplateFilter implements Query {
	public static final String QUERY = Query.REGEX;
	private static final Logger logger = LoggerFactory.getLogger(TemplateFilter.class);

	private String origin;
	private String query;
	private String search;
	private Instant modifiedBefore;
	private Instant modifiedAfter;

	public Specification<Template> spec() {
		var result = Specification.<Template>where(null);
		if (origin != null && !origin.equals("@*")) {
			result = result.and(isOrigin(origin));
		}
		if (isNotBlank(query)) {
			result = result.and(new TagQuery(query).templateSpec());
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
		return result;
	}
}
