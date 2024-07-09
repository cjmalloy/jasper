package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Ref;
import jasper.errors.NotFoundException;
import jasper.plugin.Cache;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static jasper.plugin.Cache.bannedOrBroken;
import static jasper.plugin.Cache.getCache;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Profile("file-cache")
@Component
public class FileCache {
	private static final Logger logger = LoggerFactory.getLogger(FileCache.class);
	private static final String CACHE = "cache";

	@Autowired
	RefRepository refRepository;

	@Autowired
	Storage storage;

	@Autowired
	Optional<Fetch> fetch;

	@Autowired
	Images images;

	@Autowired
	Tagger tagger;

	@Timed(value = "jasper.cache")
	public void clearDeleted(String origin) {
		var deleteLater = new ArrayList<String>();
		storage.visitStorage(origin, CACHE, id -> {
			if (!refRepository.cacheExists(id)) deleteLater.add(id);
		});
		deleteLater.forEach(id -> {
			try {
				storage.delete(origin, CACHE, id);
			} catch (IOException e) {
				logger.error("Cannot delete file", e);
			}
		});
	}

	@Timed(value = "jasper.cache", histogram = true)
	public void preFetch(String url, String origin) {
		if (exists(url, origin)) return;
		fetch(url, origin, true);
	}

	@Timed(value = "jasper.cache", histogram = true)
	public void refresh(String url, String origin) {
		fetch(url, origin, true);
	}

	private boolean exists(String url, String origin) {
		return refRepository.exists(RefFilter.builder()
			.url(url)
			.origin(origin)
			.query("_plugin/cache:!_plugin/delta/cache")
			.build().spec());
	}

	@Timed(value = "jasper.cache")
	public String fetchString(String url, String origin) {
		var cache = getCache(fetch(url, origin));
		if (cache == null) return null;
		return new String(storage.get(origin, CACHE, cache.getId()));
	}

	@Timed(value = "jasper.cache")
	public Ref fetch(String url, String origin) {
		return fetch(url, origin, null, false);
	}

	@Timed(value = "jasper.cache")
	public Ref fetch(String url, String origin, OutputStream os) {
		return fetch(url, origin, os, false);
	}

	@Timed(value = "jasper.cache")
	public Ref fetch(String url, String origin, boolean refresh) {
		return fetch(url, origin, null, refresh);
	}

	@Timed(value = "jasper.cache")
	public Ref fetch(String url, String origin, OutputStream os, boolean refresh) {
		var ref = refRepository.findOneByUrlAndOrigin(url, origin).orElse(null);
		var existingCache = getCache(ref);
		if (bannedOrBroken(existingCache)) return ref;
		if (!refresh && existingCache != null && !existingCache.isNoStore()) {
			if (os != null) storage.stream(origin, CACHE, existingCache.getId(), os);
			return ref;
		}
		if (fetch.isEmpty()) return ref;
		String mimeType;
		String id;
		try (var res = fetch.get().doScrape(url)) {
			if (res == null) return ref;
			mimeType = res.getMimeType();
			id = storage.store(origin, CACHE, res.getInputStream());
			var cache = Cache.builder()
				.id(id)
				.mimeType(mimeType)
				.contentLength(storage.size(origin, CACHE, id))
				.build();
			if (os != null) storage.stream(origin, CACHE, id, os);
			if (ref != null) {
				if (existingCache != null && isNotBlank(existingCache.getId())) {
					try {
						storage.delete(origin, CACHE, existingCache.getId());
					} catch (IOException e) {
						logger.warn("Failed to delete {}", existingCache.getId());
					}
				}
				return tagger.internalPlugin(url, origin, "_plugin/cache", cache, "-_plugin/delta/cache");
			}
			return tagger.internalPlugin(url, origin, "_plugin/cache", cache);
		} catch (Exception e) {
			tagger.attachError(origin,
				tagger.internalPlugin(url, origin, "_plugin/cache", null, "-_plugin/delta/cache"),
				"Error Fetching", e.getMessage());
		} finally {
			for (var other : createArchive(url, origin, getCache(ref))) cacheLater(other, origin);
		}
		return ref;
	}

	private Cache stat(String url, String origin) {
		return getCache(refRepository.findOneByUrlAndOrigin(url, origin).orElse(null));
	}

	@Timed(value = "jasper.cache")
	public Ref fetchThumbnail(String url, String origin, OutputStream os) {
		var fullSize = getCache(fetch(url, origin, false));
		if (fullSize == null) return null;
		if (fullSize.isThumbnail()) return fetch(url, origin, os, false);
		var thumbnailId = "t_" + fullSize.getId();
		var thumbnailUrl = "internal:" + thumbnailId;
		if (storage.exists(origin, CACHE, thumbnailId)) {
			return fetch(thumbnailUrl, origin, os, false);
		} else {
			var existingCache = stat(thumbnailUrl, origin);
			if (existingCache != null && isBlank(existingCache.getId())) {
				// If id is blank the last thumbnail generation must have failed
				// Wait for the user to manually refresh
				return null;
			}
			var data = images.thumbnail(storage.stream(origin, CACHE, fullSize.getId()));
			if (data == null) {
				// Returning null means the full size image is already small enough to be a thumbnail
				// Set this as a thumbnail to disable future attempts
				fullSize.setThumbnail(true);
				storage.stream(origin, CACHE, fullSize.getId(), os);
				return tagger.internalPlugin(url, origin, "_plugin/cache", fullSize, "-_plugin/delta/cache");
			}
			try {
				storage.storeAt(origin, CACHE, thumbnailId, data);
				if (os != null) StreamUtils.copy(data, os);
			} catch (Exception e) {
				tagger.attachError(origin,
					tagger.internalPlugin(thumbnailUrl, origin, "_plugin/cache", Cache.builder().thumbnail(true).build()),
					"Error creating thumbnail", e.getMessage());
				return null;
			}
			var cache = Cache.builder()
				.id(thumbnailId)
				.thumbnail(true)
				.mimeType("image/png")
				.contentLength((long) data.length)
				.build();
			return tagger.internalPlugin(thumbnailUrl, origin, "_plugin/cache", cache, "plugin/thumbnail");
		}
	}

	@Timed(value = "jasper.cache")
	public Ref save(String origin, InputStream in, String mimeType, String user) throws IOException {
		var id = storage.store(origin, CACHE, in);
		var cache = Cache.builder()
			.id(id)
			.mimeType(mimeType)
			.contentLength(storage.size(origin, CACHE, id))
			.build();
		return tagger.internalPlugin("internal:" + id, origin, "_plugin/cache", cache, user);
	}

	@Timed(value = "jasper.cache")
	public void overwrite(String url, String origin, byte[] bytes) throws IOException {
		var cache = getCache(fetch(url, origin));
		if (cache == null) throw new NotFoundException("Overwriting cache that does not exist");
		storage.overwrite(origin, CACHE, cache.getId(), bytes);
	}

	private List<String> createArchive(String url, String origin, Cache cache) {
		var moreScrape = new ArrayList<String>();
		if (cache == null || isBlank(cache.getId())) return moreScrape;
		// M3U8 Manifest
		var data = fetchString(url, origin);
		if (data == null) return moreScrape;
		if (data.trim().startsWith("#") && (url.endsWith(".m3u8") || cache.getMimeType().equals("application/x-mpegURL") || cache.getMimeType().equals("application/vnd.apple.mpegurl"))) {
			try {
				var urlObj = new URL(url);
				var hostPath = urlObj.getProtocol() + "://" + urlObj.getHost() + Path.of(urlObj.getPath()).getParent().toString();
				// TODO: Set archive base URL
				var basePath = "/api/v1/scrape/fetch?url=";
				var buffer = new StringBuilder();
				for (String line : data.split("\n")) {
					if (line.startsWith("#")) {
						buffer.append(line).append("\n");
					} else {
						if (!line.startsWith("http") && !line.startsWith("#")) {
							line = hostPath + "/" + line;
						}
						moreScrape.add(line);
						buffer.append(basePath).append(URLEncoder.encode(line, StandardCharsets.UTF_8)).append("\n");
					}
				}
				overwrite(url, origin, buffer.toString().getBytes());
			} catch (Exception e) {}
		}
		return moreScrape;
	}

	private void cacheLater(String url, String origin) {
		if (isBlank(url)) return;
		url = fixUrl(url);
		tagger.internalTag(url, origin, "_plugin/delta/cache");
	}

	private String fixUrl(String url) {
		// TODO: Add plugin to override like oembeds
//		return url.replaceAll("%20", "+");
		return url.replaceAll(" ", "%20");
	}
}
