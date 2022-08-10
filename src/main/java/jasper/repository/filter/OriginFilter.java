package jasper.repository.filter;

import jasper.domain.Origin;
import jasper.domain.proj.HasOrigin;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.ReplicationSpec.isModifiedAfter;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Builder
public class OriginFilter {
	public static final String QUERY = "!?" + Origin.REGEX_NOT_BLANK + "([ |]!?" + Origin.REGEX_NOT_BLANK + ")*";
	private static final Logger logger = LoggerFactory.getLogger(OriginFilter.class);

	private String query;
	private boolean local;
	private Instant modifiedAfter;

	public <T extends HasOrigin> Specification<T> spec() {
		var result = Specification.<T>where(null);
		if (isNotBlank(query)) {
			result = result.and(new TagQuery(query).originSpec());
		}
		if (local) {
			result = result.and(isOrigin(""));
		}
		if (modifiedAfter != null) {
			result = result.and(isModifiedAfter(modifiedAfter));
		}
		return result;
	}

}
