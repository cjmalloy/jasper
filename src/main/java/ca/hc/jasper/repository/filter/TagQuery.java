package ca.hc.jasper.repository.filter;

import static ca.hc.jasper.repository.spec.OriginSpec.hasAllOrigins;
import static ca.hc.jasper.repository.spec.OriginSpec.hasAnyOrigin;
import static ca.hc.jasper.repository.spec.RefSpec.hasAllQualifiedTags;
import static ca.hc.jasper.repository.spec.RefSpec.hasAnyQualifiedTag;
import static ca.hc.jasper.repository.spec.TagSpec.isAllQualifiedTag;
import static ca.hc.jasper.repository.spec.TagSpec.isAnyQualifiedTag;

import java.util.*;

import ca.hc.jasper.domain.proj.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

public class TagQuery {
	public static final String REGEX = QualifiedTag.REGEX + "([ +|:&]" + QualifiedTag.REGEX + ")*";
	private static final Logger logger = LoggerFactory.getLogger(TagQuery.class);

	private final List<List<QualifiedTag>> orGroups = new ArrayList<>();
	private final List<QualifiedTag> orTags = new ArrayList<>();

	public TagQuery(String query) {
		logger.debug(query);
		parse(query);
	}

	public <T extends HasTags> Specification<T> refSpec() {
		var result = Specification.<T>where(null);
		if (orTags.size() > 0) {
			result = result.or(hasAnyQualifiedTag(orTags));
		}
		for (var andGroup : orGroups) {
			result = result.or(hasAllQualifiedTags(andGroup));
		}
		return result;
	}

	public <T extends IsTag> Specification<T> spec() {
		var result = Specification.<T>where(null);
		if (orTags.size() > 0) {
			result = result.or(isAnyQualifiedTag(orTags));
		}
		for (var andGroup : orGroups) {
			result = result.or(isAllQualifiedTag(andGroup));
		}
		return result;
	}

	public <T extends HasOrigin> Specification<T> originSpec() {
		var result = Specification.<T>where(null);
		if (orTags.size() > 0) {
			result = result.or(hasAnyOrigin(orTags));
		}
		for (var andGroup : orGroups) {
			result = result.or(hasAllOrigins(andGroup));
		}
		return result;
	}

	private void parse(String query) {
		// TODO: Allow parentheses?
		var ors = query.split("[ +|]");
		for (var orGroup : ors) {
			var ands = orGroup.split("[:&]");
			if (ands.length == 0) continue;
			if (ands.length == 1) {
				orTags.add(new QualifiedTag(ands[0]));
			} else {
				orGroups.add(Arrays.stream(ands).map(QualifiedTag::new).toList());
			}
		}
	}

}
