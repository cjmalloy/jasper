package ca.hc.jasper.repository.filter;

import ca.hc.jasper.domain.proj.HasTags;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

@Builder
public class RefFilter {
	private static final Logger logger = LoggerFactory.getLogger(RefFilter.class);

	private TagQuery tagQuery;

	public <T extends HasTags> Specification<T> spec() {
		var result = Specification.<T>where(null);
		if (tagQuery != null) {
			result = result.and(tagQuery.spec());
		}
		return result;
	}

	public static class RefFilterBuilder {
		private TagQuery tagQuery;

		public RefFilterBuilder query(String query) {
			if (query == null) {
				tagQuery = null;
			} else {
				tagQuery = new TagQuery(query);
			}
			return this;
		}
	}
}
