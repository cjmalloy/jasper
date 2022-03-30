package ca.hc.jasper.repository.filter;

import ca.hc.jasper.domain.proj.IsTag;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

@Builder
public class TagFilter {
	private static final Logger logger = LoggerFactory.getLogger(TagFilter.class);

	private TagList tagList;

	public <T extends IsTag> Specification<T> spec() {
		var result = Specification.<T>where(null);
		if (tagList != null) {
			result = result.and(tagList.spec());
		}
		return result;
	}

	public static class TagFilterBuilder {
		private TagList tagList;

		public TagFilterBuilder query(String query) {
			if (query == null) {
				tagList = null;
			} else {
				tagList = new TagList(query);
			}
			return this;
		}
	}
}