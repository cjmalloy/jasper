package jasper.repository.filter;

import jasper.repository.spec.QualifiedTag;

public interface Query {
	String AND_REGEX = "[:&]";
	String OR_REGEX = "[ |]";
	String DELIMS = "[ |:&]";
	String SELECTOR = "!?" + QualifiedTag.REGEX;
	String AND_GROUP = SELECTOR + "([:&]" + SELECTOR + ")*";
	String OR_GROUP = "\\(" + AND_GROUP + "([ |]" + AND_GROUP + ")*\\)";
	String SELECTOR_OR_GROUP = "(" + SELECTOR + "|" + OR_GROUP + ")";
	String REGEX = SELECTOR_OR_GROUP + "([ |:&]" + SELECTOR_OR_GROUP + ")*";

	int QUERY_LEN = 512;
	String getQuery();
}
