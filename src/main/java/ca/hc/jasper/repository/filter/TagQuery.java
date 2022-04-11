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
	private final List<List<List<QualifiedTag>>> nestedGroups = new ArrayList<>();
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
		for (var andGroup : nestedGroups) {
			var subExpression = Specification.<T>where(null);
			for (var innerOrGroup : andGroup) {
				subExpression = subExpression.and(hasAnyQualifiedTag(innerOrGroup));
			}
			result = result.or(subExpression);
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
		for (var andGroup : nestedGroups) {
			var subExpression = Specification.<T>where(null);
			for (var innerOrGroup : andGroup) {
				subExpression = subExpression.and(isAnyQualifiedTag(innerOrGroup));
			}
			result = result.or(subExpression);
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
		for (var andGroup : nestedGroups) {
			var subExpression = Specification.<T>where(null);
			for (var innerOrGroup : andGroup) {
				subExpression = subExpression.and(hasAnyOrigin(innerOrGroup));
			}
			result = result.or(subExpression);
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
		for (var andGroup : nestedGroups) {
			var subExpression = Specification.<Template>where(null);
			for (var innerOrGroup : andGroup) {
				subExpression = subExpression.and(matchesAnyQualifiedTag(innerOrGroup));
			}
			result = result.or(subExpression);
		}
		return result;
	}

	private void parse(String query) {
		logger.debug(query);
		query = markInnerOuterOrs(query);
		query = query.replaceAll(AND_REGEX, ":");
		var ors = query.split(" ");
		for (var orGroup : ors) {
			if (orGroup.length() == 0) continue;
			if (!orGroup.contains(":")) {
				if (orGroup.contains("|")) {
					// Useless parentheses around ors
					orTags.addAll(Arrays.stream(orGroup.split("[()|]")).map(QualifiedTag::new).toList());
				} else {
					orTags.add(new QualifiedTag(orGroup));
				}
			} else {
				if (!orGroup.contains("|")) {
					orGroups.add(Arrays.stream(orGroup.split(":")).map(QualifiedTag::new).toList());
				} else {
					var andGroups = orGroup.split("[():]");
					var nested = new ArrayList<List<QualifiedTag>>();
					for (var andGroup : andGroups) {
						if (andGroup.length() == 0) continue;
						if (!andGroup.contains("|")) {
							nested.add(List.of(new QualifiedTag(andGroup)));
						} else {
							nested.add(Arrays.stream(andGroup.split("\\|")).map(QualifiedTag::new).toList());
						}
					}
					nestedGroups.add(nested);
				}
			}
		}
	}

	private String markInnerOuterOrs(String query) {
		if (!query.contains("(")) return query.replaceAll(OR_REGEX, " ");
		var parens = query.startsWith("(");
		var groups = query.split("[()]");
		var result = new StringBuilder();
		for (String group : groups) {
			if (parens) {
				result.append("(");
				result.append(group.replaceAll(OR_REGEX, " "));
				result.append(")");
			} else {
				result.append(group.replaceAll(OR_REGEX, "|"));
			}
			parens = !parens;
		}
		return result.toString();
	}

	private String sanitize(String query) {
		return query
			.replaceAll("\\s*(" + DELIMS + ")\\s*", "$1")
			.trim();
	}

}
