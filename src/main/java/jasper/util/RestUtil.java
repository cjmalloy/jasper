package jasper.util;

import jasper.domain.proj.HasModified;
import jasper.service.dto.RefDto;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.stream.Collectors;

public class RestUtil {

	public static CacheControl ifNotModifiedCacheControl = CacheControl
			.noCache()
			.mustRevalidate()
			.cachePrivate();

	public static <T extends HasModified> ResponseEntity<T> ifNotModified(WebRequest request, T result) {
		return ifNotModified(request, result, getModified(result));
	}

	public static <T extends HasModified> ResponseEntity<List<T>> ifNotModifiedList(WebRequest request, List<T> result) {
		return ifNotModified(request, result, getModifiedList(result));
	}

	public static <T extends HasModified> ResponseEntity<Page<T>> ifNotModifiedPage(WebRequest request, Page<T> result) {
		return ifNotModified(request, result, getModifiedPage(result));
	}

	public static <T> ResponseEntity<T> ifNotModified(WebRequest request, T result, GetModified<T> m) {
		return ifNotModified(request, result, m.getModified(result));
	}

	public static <T> ResponseEntity<T> ifNotModified(WebRequest request, T result, String modified) {
		if (modified == null) return ResponseEntity.ok(result);
		if (request.checkNotModified(modified)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
				.cacheControl(ifNotModifiedCacheControl)
				.build();
		}
		return ResponseEntity.ok()
			.cacheControl(ifNotModifiedCacheControl)
			.eTag(modified)
			.body(result);
	}

	private static <T extends HasModified> String getModifiedList(List<T> result) {
		return result.stream()
			.map(RestUtil::getModified)
			.collect(Collectors.joining(","));
	}

	private static <T extends HasModified> String getModifiedPage(Page<T> result) {
		if (result.getContent().size() > 100) return null;
		return result.stream()
			.map(RestUtil::getModified)
			.collect(Collectors.joining(",")) +
			";" + result.isFirst() +
			"," + result.isLast();
	}

	private static <T extends HasModified> String getModified(T result) {
		if (result == null) return "";
		var modified = result.getModified().toString();
		if (!(result instanceof RefDto ref)) return modified;
		if (ref.getMetadata() == null) return modified;
		if (ref.getMetadata().getModified() == null) return modified;
		if (ref.getMetadata().getModified().isBefore(result.getModified())) return modified;
		return ref.getMetadata().getModified().toString();
	}

	public interface GetModified<T> {
		String getModified(T entity);
	}
}
