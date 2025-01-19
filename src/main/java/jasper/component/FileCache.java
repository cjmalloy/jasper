package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Ref;
import jasper.errors.NotFoundException;
import jasper.errors.ScrapeProtocolException;
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
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static jasper.plugin.Cache.bannedOrBroken;
import static jasper.plugin.Cache.getCache;
import static jasper.plugin.Pull.getPull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Profile("file-cache")
@Component
public class FileCache {
	private static final Logger logger = LoggerFactory.getLogger(FileCache.class);
	static final String CACHE = "cache";

	@Autowired
	ConfigCache configs;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Storage storage;

	@Autowired
	Fetch fetch;

	@Autowired
	Images images;

	@Autowired
	Tagger tagger;

	@Timed(value = "jasper.cache")
	public void clearDeleted(String origin) {
		logger.info("{} Purging file cache", origin);
		var start = Instant.now();
		storage.visitStorage(origin, CACHE, id -> {
			if (!refRepository.cacheExists(id)) {
				try {
					storage.delete(origin, CACHE, id);
				} catch (IOException e) {
					logger.error("Cannot delete file", e);
				}
			}
		});
		logger.info("{} Finished purging file cache in {}", origin, Duration.between(start, Instant.now()));
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
		if (bannedOrBroken(cache)) return null;
		return new String(storage.get(origin, CACHE, cache.getId()));
	}

	@Timed(value = "jasper.cache")
	public byte[] fetchBytes(String url, String origin) {
		var cache = getCache(fetch(url, origin));
		if (cache == null) return null;
		if (bannedOrBroken(cache)) return null;
		return storage.get(origin, CACHE, cache.getId());
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
		if (bannedOrBroken(existingCache, refresh)) return ref;
		if (!refresh && existingCache != null && !existingCache.isNoStore() && storage.exists(origin, CACHE, existingCache.getId())) {
			if (os != null) storage.stream(origin, CACHE, existingCache.getId(), os);
			return ref;
		}
		var remote = configs.getRemote(origin);
		var pull = getPull(remote);
		if (url.startsWith("cache:")) {
			var id = url.substring("cache:".length());
			if (storage.exists(origin, CACHE, id)) {
				if (os != null) storage.stream(origin, CACHE, id, os);
				return ref;
			} else if (remote == null) {
				// No cache found and no remote to fetch from
				return ref;
			}
		}
		String mimeType;
		String id;
		try (var res = fetch.doScrape(url, origin)) {
			if (res == null) return ref;
			if (remote != null && (url.startsWith("cache:") || pull.isCacheProxy())) {
				id = url.substring("cache:".length());
				if (os != null && storage.exists(origin, CACHE, id)) storage.stream(origin, CACHE, id, os);
				return ref;
			}
			mimeType = res.getMimeType();
			if (existingCache != null && isNotBlank(existingCache.getId()) && !storage.exists(origin, CACHE, existingCache.getId())) {
				storage.storeAt(origin, CACHE, existingCache.getId(), res.getInputStream());
				return ref;
			}
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
				if (remote != null) return tagger.silentPlugin(url, origin, "_plugin/cache", cache, "-_plugin/delta/cache");
				return tagger.internalPlugin(url, origin, "_plugin/cache", cache, "-_plugin/delta/cache");
			}
			if (remote != null) return ref = tagger.silentPlugin(url, origin, "_plugin/cache", cache);
			return ref = tagger.internalPlugin(url, origin, "_plugin/cache", cache);
		} catch (ScrapeProtocolException e) {
			throw e;
		} catch (Exception e) {
			var err = remote != null
				? tagger.silentPlugin(url, origin, "_plugin/cache", null, "-_plugin/delta/cache")
				: tagger.internalPlugin(url, origin, "_plugin/cache", null, "-_plugin/delta/cache");
			tagger.attachError(remote != null ? remote.getOrigin() : origin, err, "Error Fetching", e.getMessage());
			if (remote != null) {
				var cache = existingCache != null ? existingCache : Cache.builder().build();
				cache.setBan(true);
				return tagger.silentPlugin(url, origin, "_plugin/cache", cache);
			}
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
		var id = "";
		if (url.startsWith("cache:")) {
			id = url.substring("cache:".length());
		}
		var fullSize = getCache(fetch(url, origin, false));
		if (fullSize == null) {
			if (configs.getRemote(origin) == null) return null;
		} else {
			id = fullSize.getId();
		}
		if (isBlank(id)) return null;
		if (bannedOrBroken(fullSize)) return null;
		if (fullSize != null && fullSize.isThumbnail()) return fetch(url, origin, os, false);
		var thumbnailId = "t_" + id;
		var thumbnailUrl = "cache:" + thumbnailId;
		var existingCache = stat(thumbnailUrl, origin);
		if (existingCache != null && isBlank(existingCache.getId())) {
			// If id is blank the last thumbnail generation must have failed
			// Wait for the user to manually refresh
			return null;
		}
		if (storage.exists(origin, CACHE, thumbnailId)) {
			return fetch(thumbnailUrl, origin, os, false);
		} else {
			var remote = configs.getRemote(origin);
			var data = images.thumbnail(storage.stream(origin, CACHE, id));
			if (data == null) {
				// Returning null means the full size image is already small enough to be a thumbnail
				// Set this as a thumbnail to disable future attempts
				if (fullSize != null) fullSize.setThumbnail(true);
				storage.stream(origin, CACHE, id, os);
				if (remote != null) return tagger.silentPlugin(url, origin, "_plugin/cache", fullSize, "-_plugin/delta/cache");
				return tagger.internalPlugin(url, origin, "_plugin/cache", fullSize, "-_plugin/delta/cache");
			}
			try {
				if (storage.exists(origin, CACHE, thumbnailId)) {
					storage.delete(origin, CACHE, thumbnailId);
				}
				storage.storeAt(origin, CACHE, thumbnailId, data);
				if (os != null) StreamUtils.copy(data, os);
			} catch (Exception e) {
				var err = remote == null
					? tagger.silentPlugin(thumbnailUrl, origin, "_plugin/cache", Cache.builder().thumbnail(true).build())
					: tagger.internalPlugin(thumbnailUrl, origin, "_plugin/cache", Cache.builder().thumbnail(true).build());
				tagger.attachError(remote != null ? remote.getOrigin() : origin, err, "Error creating thumbnail", e.getMessage());
				if (remote != null) {
					var cache = existingCache != null ? existingCache : Cache.builder().build();
					cache.setBan(true);
					return tagger.silentPlugin(url, origin, "_plugin/cache", cache);
				}
				return null;
			}
			var cache = Cache.builder()
				.id(thumbnailId)
				.thumbnail(true)
				.mimeType("image/png")
				.contentLength((long) data.length)
				.build();
			if (remote != null) return tagger.silentPlugin(thumbnailUrl, origin, "_plugin/cache", cache, "plugin/thumbnail");
			return tagger.internalPlugin(thumbnailUrl, origin, "_plugin/cache", cache, "plugin/thumbnail");
		}
	}

	@Timed(value = "jasper.cache")
	public Ref save(String origin, String title, InputStream in, String mimeType, String ...tags) throws IOException {
		var id = storage.store(origin, CACHE, in);
		var cache = Cache.builder()
			.id(id)
			.mimeType(mimeType)
			.contentLength(storage.size(origin, CACHE, id))
			.build();
		return tagger.newPlugin("cache:" + id, title, origin, "_plugin/cache", cache, tags);
	}

	@Timed(value = "jasper.cache")
	public void overwrite(String url, String origin, byte[] bytes) throws IOException {
		var cache = getCache(fetch(url, origin));
		if (cache == null) throw new NotFoundException("Overwriting cache that does not exist");
		storage.overwrite(origin, CACHE, cache.getId(), bytes);
	}

	@Timed(value = "jasper.cache")
	public void push(String url, String origin, InputStream in) throws IOException {
		if (!url.startsWith("cache:")) throw new NotFoundException("URL is not cacheable");
		storage.storeAt(origin, CACHE, url.substring("cache:".length()), in);
	}

	@Timed(value = "jasper.cache")
	public void push(String url, String origin, byte[] data) throws IOException {
		if (!url.startsWith("cache:")) throw new NotFoundException("URL is not cacheable");
		var id = url.substring("cache:".length());
		if (storage.exists(origin, CACHE, id)) {
			storage.overwrite(origin, CACHE, id, data);
		} else {
			storage.storeAt(origin, CACHE, id, data);
		}
	}

	@Timed(value = "jasper.cache")
	public boolean cacheExists(String url, String origin) {
		if (!url.startsWith("cache:")) throw new NotFoundException("URL is not cacheable");
		return storage.exists(origin, CACHE, url.substring("cache:".length()));
	}

	private String fetchExistingString(String url, String origin) {
		var ref = refRepository.findOneByUrlAndOrigin(url, origin).orElse(null);
		var cache = getCache(ref);
		if (cache == null) return null;
		if (bannedOrBroken(cache)) return null;
		return new String(storage.get(origin, CACHE, cache.getId()));
	}

	private List<String> createArchive(String url, String origin, Cache cache) {
		var moreScrape = new ArrayList<String>();
		if (cache == null || isBlank(cache.getId())) return moreScrape;
		// M3U8 Manifest
		var data = fetchExistingString(url, origin);
		if (data == null) return moreScrape;
		try {
			var urlObj = URI.create(url).toURL();
			if (data.trim().startsWith("#") && (urlObj.getPath().endsWith(".m3u8") || cache.getMimeType().equalsIgnoreCase("application/x-mpegURL") || cache.getMimeType().equalsIgnoreCase("application/vnd.apple.mpegurl"))) {
				var hostPath = urlObj.getProtocol() + "://" + urlObj.getHost() + Path.of(urlObj.getPath()).getParent().toString();
				// TODO: Set archive base URL
				var basePath = isNotBlank(origin) ? "/api/v1/proxy?origin=" + origin + "&url=" : "/api/v1/proxy?url=";
				var buffer = new StringBuilder();
				for (var line : data.split("\n")) {
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
			}
		} catch (Exception e) {}
		return moreScrape;
	}

	private void cacheLater(String url, String origin) {
		if (isBlank(url)) return;
		url = fixUrl(url);
		var ref = refRepository.findOneByUrlAndOrigin(url, origin).orElse(null);
		if (ref != null && (ref.hasTag("_plugin/cache") || ref.hasTag("_plugin/delta/cache"))) return;
		tagger.internalTag(url, origin, "_plugin/delta/cache");
	}

	private String fixUrl(String url) {
		// TODO: Add plugin to override like oembeds
//		return url.replaceAll("%20", "+");
		return url.replaceAll(" ", "%20");
	}
}
