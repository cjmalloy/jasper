package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.plugin.Cache;
import jasper.repository.RefRepository;
import org.apache.commons.io.output.CountingOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.UUID;

import static jasper.plugin.Cache.bannedOrBroken;
import static jasper.plugin.Cache.getCache;

@Component
public class Proxy {
	private static final Logger logger = LoggerFactory.getLogger(Proxy.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Optional<Fetch> fetch;

	@Autowired
	Optional<FileCache> fileCache;

	@Autowired
	Tagger tagger;

	@Autowired
	Images images;

	@Timed(value = "jasper.proxy")
	public Cache stat(String url, String origin) {
		return getCache(refRepository.findOneByUrlAndOrigin(url, origin).orElse(null));
	}

	@Timed(value = "jasper.proxy")
	public String fetchString(String url) {
		return fetchString(url, null, false);
	}

	@Timed(value = "jasper.proxy")
	public String fetchString(String url, String origin, boolean cache) {
		if (cache && fileCache.isPresent()) {
			return fileCache.get().fetchString(url, origin);
		}
		if (fetch.isEmpty()) return null;
		try (var res = fetch.get().doScrape(url, origin)) {
			return new String(res.getInputStream().readAllBytes());
		} catch (Exception e) {
			tagger.attachError(origin,
				tagger.internalPlugin(url, origin, "_plugin/cache", null),
				"Error Fetching", e.getMessage());
		}
		return null;
	}

	@Timed(value = "jasper.proxy")
	public Cache fetch(String url, String origin) {
		if (fileCache.isPresent()) {
			return getCache(fileCache.get().fetch(url, origin));
		}
		return stat(url, origin);
	}

	@Timed(value = "jasper.proxy")
	public Cache fetch(String url, String origin, OutputStream os) {
		if (fileCache.isPresent()) {
			return getCache(fileCache.get().fetch(url, origin, os));
		}
		var existingCache = stat(url, origin);
		if (bannedOrBroken(existingCache)) return existingCache;
		if (fetch.isEmpty()) return existingCache;
		try (var res = fetch.get().doScrape(url, origin)) {
			var cos = new CountingOutputStream(os);
			StreamUtils.copy(res.getInputStream(), cos);
			res.close();
			return Cache.builder()
				.id("nostore_" + UUID.randomUUID())
				.mimeType(res.getMimeType())
				.contentLength(cos.getByteCount())
				.build();
		} catch (Exception e) {
			tagger.attachError(origin,
				tagger.internalPlugin(url, origin, "_plugin/cache", null),
				"Error Fetching", e.getMessage());
		}
		return existingCache;
	}

	@Timed(value = "jasper.proxy")
	public Cache fetchThumbnail(String url, String origin, OutputStream os) {
		if (fileCache.isPresent()) {
			return getCache(fileCache.get().fetchThumbnail(url, origin, os));
		}
		var fullSize = stat(url, origin);
		if (bannedOrBroken(fullSize)) return fullSize;
		if (fullSize != null && fullSize.isThumbnail()) {
			return fetch(url, origin, os);
		}
		if (fetch.isEmpty()) return fullSize;
		try (var res = fetch.get().doScrape(url, origin)) {
			var bytes = res.getInputStream().readAllBytes();
			res.close();
			var data = images.thumbnail(new ByteArrayInputStream(bytes));
			if (data == null) {
				// Returning null means the full size image is already small enough to be a thumbnail
				// Set this as a thumbnail to disable future attempts
				fullSize.setThumbnail(true);
				StreamUtils.copy(bytes, os);
				tagger.internalPlugin(url, origin, "_plugin/cache", fullSize, "-_plugin/delta/cache");
				return fullSize;
			}
			StreamUtils.copy(data, os);
			return Cache.builder()
				.id("nostore_" + UUID.randomUUID())
				.thumbnail(true)
				.mimeType("image/png")
				.contentLength((long) data.length)
				.build();
		} catch (Exception e) {
			tagger.attachError(origin,
				refRepository.findOneByUrlAndOrigin(url, origin).orElseThrow(),
				"Error creating thumbnail", e.getMessage());
		}
		return Cache.builder().thumbnail(true).build();
	}

}
