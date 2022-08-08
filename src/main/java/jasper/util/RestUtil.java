package jasper.util;

import jasper.domain.proj.HasModified;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class RestUtil {

	public static CacheControl ifNotModifiedCacheControl = CacheControl
			.noCache()
			.mustRevalidate()
			.cachePrivate();

	public static <T extends HasModified> ResponseEntity<T> ifModifiedSince(WebRequest request, T result) {
		return ifModifiedSince(request, result, result.getModified());
	}

	public static <T extends HasModified> ResponseEntity<List<T>> ifModifiedSinceList(WebRequest request, List<T> result) {
		return ifModifiedSince(request, result, getMaxModified(result));
	}

	public static <T extends HasModified> ResponseEntity<Page<T>> ifModifiedSincePage(WebRequest request, Page<T> result) {
		return ifModifiedSince(request, result, getMaxModified(result.getContent()));
	}

	public static <T> ResponseEntity<T> ifModifiedSince(WebRequest request, T result, GetModified<T> m) {
		return ifModifiedSince(request, result, m.getModified(result));
	}

	public static <T> ResponseEntity<T> ifModifiedSince(WebRequest request, T result, Instant lastModified) {
		if (lastModified == null) return ResponseEntity.ok(result);
		var ms = lastModified.toEpochMilli();
		if (request.checkNotModified(ms)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
				.cacheControl(ifNotModifiedCacheControl)
				.build();
		}
		return ResponseEntity.ok()
			.cacheControl(ifNotModifiedCacheControl)
			.lastModified(ms)
			.body(result);
	}

	public static <T extends HasModified> Instant getMaxModified(List<T> list) {
		return list.stream()
			.max(Comparator.comparing(HasModified::getModified))
			.map(HasModified::getModified)
			.orElse(null);
	}

	public static boolean sortedByTime(Pageable pageable) {
		return pageable.getSort().get().allMatch(
			col ->
				col.getProperty().equals("created") ||
				col.getProperty().equals("modified") ||
				col.getProperty().equals("published"));
	}

	public interface GetModified<T> {
		Instant getModified(T entity);
	}
}
