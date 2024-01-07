package jasper.component;

import jasper.config.Props;
import jasper.domain.proj.Cursor;
import jasper.service.dto.RefDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.getCommonPrefix;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.reverse;

@Component
public class HttpCache {
	private static final Logger logger = LoggerFactory.getLogger(HttpCache.class);

	public static CacheControl ifNotModifiedCacheControl = CacheControl
			.noCache()
			.mustRevalidate()
			.cachePrivate();

	@Autowired
	Props props;

	public <T extends Cursor> ResponseEntity<T> ifNotModified(WebRequest request, T result) {
		return ifNotModified(request, result, getModified(result));
	}

	public <T extends Cursor> ResponseEntity<List<T>> ifNotModifiedList(WebRequest request, List<T> result) {
		return ifNotModified(request, result, getModifiedList(result));
	}

	public <T extends Cursor> ResponseEntity<Page<T>> ifNotModifiedPage(WebRequest request, Page<T> result) {
		return ifNotModified(request, result, getModifiedPage(result));
	}

	public <T> ResponseEntity<T> ifNotModified(WebRequest request, T result, GetModified<T> m) {
		return ifNotModified(request, result, m.getModified(result));
	}

	public <T> ResponseEntity<T> ifNotModified(WebRequest request, T result, String modified) {
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

	private <T extends Cursor> String getModifiedList(List<T> result) {
		return result.stream()
			.map(this::getModified)
			.collect(Collectors.joining(","));
	}

	private <T extends Cursor> String getModifiedPage(Page<T> result) {
		if (result.getContent().size() > props.getMaxEtagPageSize()) return null;
		var etag = new ArrayList<>(result.stream().map(this::getModified).toList());
		for (var i = etag.size() - 1; i > 0; i--) {
			var a = etag.get(i - 1);
			var b = etag.get(i);
			var prefix = getCommonPrefix(a, b);
			var suffix = reverse(getCommonPrefix(reverse(a), reverse(b)));
			if (prefix.length() + suffix.length() >= b.length()) {
				etag.set(i, "");
			} else if (isNotBlank(prefix)) {
				logger.trace("Etag Compression: {}, {}, prefix: {}, suffix: {}", a, b, prefix, suffix);
				etag.set(i, Integer.toHexString(prefix.length()) + "/" + b.substring(prefix.length(), b.length() - suffix.length()));
			}
		}
		return String.join(",", etag) +
			";" + result.isFirst() +
			"," + result.isLast();
	}

	private <T extends Cursor> String getModified(T result) {
		if (result == null) return "";
		var modified = result.getModified().truncatedTo(ChronoUnit.MILLIS).toString();
		if (!(result instanceof RefDto ref)) return modified;
		if (ref.getMetadata() == null) return modified;
		if (ref.getMetadata().getModified() == null) return modified;
		try {
			if (Instant.parse(ref.getMetadata().getModified()).isBefore(result.getModified())) return modified;
		} catch (DateTimeParseException ignored) {}
		return ref.getMetadata().getModified();
	}

	public interface GetModified<T> {
		String getModified(T entity);
	}
}
