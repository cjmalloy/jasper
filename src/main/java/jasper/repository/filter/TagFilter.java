package jasper.repository.filter;

import jasper.domain.proj.Tag;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.ReplicationSpec.isModifiedAfter;
import static jasper.repository.spec.TagSpec.searchTagOrName;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Builder
@Getter
public class TagFilter implements Query {
	public static final String QUERY = Query.REGEX;
	private static final Logger logger = LoggerFactory.getLogger(TagFilter.class);

	private String origin;
	private String query;
	private String search;
	private Instant modifiedAfter;

	public <T extends Tag> Specification<T> spec() {
		var result = Specification.<T>where(null);
		if (isNotBlank(origin)) {
			result = result.and(isOrigin(origin));
		}
		if (isNotBlank(query)) {
			result = result.and(new TagQuery(query).spec());
		}
		if (isNotBlank(search)) {
			result = result.and(searchTagOrName(search));
		}
		if (modifiedAfter != null) {
			result = result.and(isModifiedAfter(modifiedAfter));
		}
		return result;
	}
}
