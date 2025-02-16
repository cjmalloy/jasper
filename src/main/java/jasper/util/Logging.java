package jasper.util;

public class Logging {
	public static String getMessage(Throwable e) {
		if (e.getCause() != null) {
			return e.getClass().getName() + " " + e.getMessage() + ": " + getMessage(e.getCause());
		}
		return e.getClass().getName() + " " + e.getMessage();
	}
}
