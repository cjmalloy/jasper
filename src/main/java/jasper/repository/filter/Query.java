package jasper.repository.filter;

public interface Query {
	/**
	 * Allowed characters in a query. The structure is validated by {@link TagQuery}:
	 * terms joined with <code>:</code> (and) or <code>|</code> (or), groups in
	 * parentheses nested to any depth, and <code>!</code> negating a single term
	 * or a whole group.
	 */
	String REGEX = "[!_+a-z0-9/.@|:()*]+";

	int QUERY_LEN = 4096;
	int SEARCH_LEN = 512;

	String getQuery();
}
