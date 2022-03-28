package ca.hc.jasper.repository.filter;

import static ca.hc.jasper.repository.spec.TagSpec.isAnyTag;

import java.util.Arrays;
import java.util.List;

import ca.hc.jasper.domain.Tag;
import ca.hc.jasper.domain.proj.IsTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

public class TagList {
	public static final String REGEX = Tag.REGEX + "([ +|]" + Tag.REGEX + ")*";
	private static final Logger logger = LoggerFactory.getLogger(TagList.class);

	private final List<String> tags;

	public TagList(String query) {
		logger.debug(query);
		tags = Arrays.asList(query.split("[ +|]"));
	}

	public <T extends IsTag> Specification<T> spec() {
		return isAnyTag(tags);
	}

}
