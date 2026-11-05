package jasper;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the SQL Hibernate renders, so tests can check the real SQL instead of a copy.
 * Registered for all tests, but only records on the current thread inside {@link #capture}.
 */
public class SqlCapture implements StatementInspector {
	private static final ThreadLocal<List<String>> captured = new ThreadLocal<>();

	/**
	 * Runs the action and returns every SQL statement Hibernate prepared on this thread.
	 */
	public static List<String> capture(Runnable action) {
		var sql = new ArrayList<String>();
		captured.set(sql);
		try {
			action.run();
		} finally {
			captured.remove();
		}
		return sql;
	}

	@Override
	public String inspect(String sql) {
		var list = captured.get();
		if (list != null) list.add(sql);
		return sql;
	}
}
