package ca.hc.jasper.repository.filter;

import static ca.hc.jasper.repository.filter.Query.*;
import static ca.hc.jasper.repository.spec.OriginSpec.hasAllOrigins;
import static ca.hc.jasper.repository.spec.OriginSpec.hasAnyOrigin;
import static ca.hc.jasper.repository.spec.RefSpec.hasAllQualifiedTags;
import static ca.hc.jasper.repository.spec.RefSpec.hasAnyQualifiedTag;
import static ca.hc.jasper.repository.spec.TagSpec.isAllQualifiedTag;
import static ca.hc.jasper.repository.spec.TagSpec.isAnyQualifiedTag;
import static ca.hc.jasper.repository.spec.TemplateSpec.matchesAllQualifiedTag;
import static ca.hc.jasper.repository.spec.TemplateSpec.matchesAnyQualifiedTag;

import java.util.*;

import ca.hc.jasper.domain.Template;
import ca.hc.jasper.domain.proj.*;
import ca.hc.jasper.repository.spec.QualifiedTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;

public class TagQuery {
	private static final Logger logger = LoggerFactory.getLogger(TagQuery.class);

	private final List<List<QualifiedTag>> orGroups = new ArrayList<>();
	private final List<QualifiedTag> orTags = new ArrayList<>();

	public TagQuery(String query) {
		parse(sanitize(query));
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

	public Specification<Template> templateSpec() {
		var result = Specification.<Template>where(null);
		if (orTags.size() > 0) {
			result = result.or(matchesAnyQualifiedTag(orTags));
		}
		for (var andGroup : orGroups) {
			result = result.or(matchesAllQualifiedTag(andGroup));
		}
		return result;
	}

	private void parse(String query) {
		logger.debug(query);
		// TODO: Allow parentheses?
		var ors = query.split(OR_REGEX);
		for (var orGroup : ors) {
			var ands = orGroup.split(AND_REGEX);
			if (ands.length == 0) continue;
			if (ands.length == 1) {
				orTags.add(new QualifiedTag(ands[0]));
			} else {
				orGroups.add(Arrays.stream(ands).map(QualifiedTag::new).toList());
			}
		}
	}

	private String sanitize(String query) {
		return query
			.replaceAll("\\s*(" + DELIMS + ")\\s*", "$1")
			.trim();
	}

}
