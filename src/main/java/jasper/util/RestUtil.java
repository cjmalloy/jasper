package jasper.util;

import jasper.domain.proj.HasModified;
import jasper.service.dto.RefDto;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class RestUtil {

	public static CacheControl ifNotModifiedCacheControl = CacheControl
			.noCache()
			.mustRevalidate()
			.cachePrivate();

	public static <T extends HasModified> ResponseEntity<T> ifModifiedSince(WebRequest request, T result) {
		return ifModifiedSince(request, result, getModified(result));
	}

	public static <T extends HasModified> ResponseEntity<List<T>> ifModifiedSinceList(WebRequest request, List<T> result) {
		return ifModifiedSince(request, result, getMaxModified(result));
	}

	public static <T> ResponseEntity<T> ifModifiedSince(WebRequest request, T result, GetModified<T> m) {
		return ifModifiedSince(request, result, m.getModified(result));
	}

	public static <T> ResponseEntity<T> ifModifiedSince(WebRequest request, T result, Instant lastModified) {
		if (lastModified == null) return ResponseEntity.ok(result);
		var eTag = lastModified.toString();
		if (request.checkNotModified(eTag)) {
			return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
				.cacheControl(ifNotModifiedCacheControl)
				.build();
		}
		return ResponseEntity.ok()
			.cacheControl(ifNotModifiedCacheControl)
			.eTag(eTag)
			.body(result);
	}

	private static <T extends HasModified> Instant getModified(T result) {
		var modified = result.getModified();
		if (!(result instanceof RefDto ref)) return modified;
		if (ref.getMetadata() == null) return modified;
		if (ref.getMetadata().getModified() == null) return modified;
		if (ref.getMetadata().getModified().isBefore(modified)) return modified;
		return ref.getMetadata().getModified();
	}

	public static <T extends HasModified> Instant getMaxModified(List<T> list) {
		if (list.stream().anyMatch(Objects::isNull)) {
			// Do not cache if an object was deleted
			return null;
		}
		return list.stream()
			.max(Comparator.comparing(RestUtil::getModified))
			.map(HasModified::getModified)
			.orElse(null);
	}

	public interface GetModified<T> {
		Instant getModified(T entity);
	}
}
