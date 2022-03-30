package ca.hc.jasper.repository.filter;

import static ca.hc.jasper.repository.spec.ReplicationSpec.isModifiedAfter;

import java.time.Instant;

import ca.hc.jasper.domain.proj.HasOrigin;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

@Builder
public class OriginFilter {
	private static final Logger logger = LoggerFactory.getLogger(OriginFilter.class);

	private OriginList originList;
	private Instant modifiedAfter;

	public <T extends HasOrigin> Specification<T> spec() {
		var result = Specification.<T>where(null);
		if (modifiedAfter != null) {
			result = result.and(isModifiedAfter(modifiedAfter));
		}
		if (originList != null) {
			result = result.and(originList.spec());
		}
		return result;
	}

	public static class OriginFilterBuilder {
		private OriginList originList;

		public OriginFilterBuilder query(String query) {
			if (query == null) {
				originList = null;
			} else {
				originList = new OriginList(query);
			}
			return this;
		}
	}
}
