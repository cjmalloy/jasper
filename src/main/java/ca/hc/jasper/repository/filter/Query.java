package ca.hc.jasper.repository.filter;

import ca.hc.jasper.repository.spec.QualifiedTag;

public interface Query {
	String AND_REGEX = "[:&]";
	String OR_REGEX = "[ |]";
	String DELIMS = "[ |:&]";
	String SELECTOR = "!?" + QualifiedTag.REGEX;
	String REGEX = SELECTOR + "([ |:&]" + SELECTOR + ")*";
	String getQuery();
}
