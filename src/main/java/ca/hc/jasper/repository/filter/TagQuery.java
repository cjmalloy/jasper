package ca.hc.jasper.repository.filter;

import static ca.hc.jasper.repository.spec.RefSpec.hasAllTags;
import static ca.hc.jasper.repository.spec.RefSpec.hasAnyTag;

import java.util.*;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.domain.TagId;
import ca.hc.jasper.domain.proj.HasTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

public class TagQuery {
	public static final String REGEX = TagId.REGEX + "([ +|:&]" + TagId.REGEX + ")*";
	private static final Logger logger = LoggerFactory.getLogger(TagQuery.class);

	private final List<List<String>> orGroups = new ArrayList<>();
	private final List<String> orTags = new ArrayList<>();

	public TagQuery(String query) {
		logger.debug(query);
		parse(query);
	}

	public <T extends HasTags> Specification<T> spec() {
		var result = Specification.<T>where(null);
		if (orTags.size() > 0) {
			result = result.or(hasAnyTag(orTags));
		}
		for (var andGroup : orGroups) {
			result = result.or(hasAllTags(andGroup));
		}
		return result;
	}

	private void parse(String query) {
		// TODO: Allow parentheses?
		var ors = query.split("[ +|]");
		for (var orGroup : ors) {
			var ands = orGroup.split("[:&]");
			if (ands.length == 0) continue;;
			if (ands.length == 1) {
				orTags.add(ands[0]);
			} else {
				orGroups.add(Arrays.asList(ands));
			}
		}
	}

}
