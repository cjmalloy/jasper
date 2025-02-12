package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Ref;
import jasper.repository.RefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static jasper.plugin.Cache.bannedOrBroken;
import static jasper.plugin.Cache.getCache;

@Component
public class Proxy {
	private static final Logger logger = LoggerFactory.getLogger(Proxy.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Fetch fetch;

	@Autowired
	Optional<FileCache> fileCache;

	@Autowired
	Tagger tagger;

	@Autowired
	Images images;

	@Timed(value = "jasper.proxy")
	public Ref stat(String url, String origin) {
		return refRepository.findOneByUrlAndOrigin(url, origin).orElse(null);
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
		try (var res = fetch.doScrape(url, origin)) {
			return new String(res.getInputStream().readAllBytes());
		} catch (Exception e) {
			if (refRepository.existsByUrlAndOrigin(url, origin)) {
				tagger.attachError(origin,
					tagger.plugin(url, origin, "_plugin/cache", null),
					"Error Fetching String", e.getMessage());
			}
		}
		return null;
	}

	@Timed(value = "jasper.proxy")
	public InputStream fetch(String url, String origin) {
		if (fileCache.isPresent()) {
			return fileCache.get().fetch(url, origin);
		}
		if (bannedOrBroken(getCache(stat(url, origin)))) return null;
		try (var res = fetch.doScrape(url, origin)) {
			if (res == null) return null;
			return res.getInputStream();
		} catch (Exception e) {
			tagger.attachError(origin,
				tagger.plugin(url, origin, "_plugin/cache", null),
				"Error Proxying", e.getMessage());
			return null;
		}
	}

	@Timed(value = "jasper.proxy")
	public InputStream fetchThumbnail(String url, String origin) {
		if (fileCache.isPresent()) {
			return fileCache.get().fetchThumbnail(url, origin);
		}
		var fullSize = getCache(stat(url, origin));
		if (bannedOrBroken(fullSize)) return null;
		if (fullSize != null && fullSize.isThumbnail()) {
			return fetch(url, origin);
		}
		try (var res = fetch.doScrape(url, origin)) {
			var bytes = res.getInputStream().readAllBytes();
			res.close();
			var data = images.thumbnail(bytes);
			if (data == null) {
				// Returning null means the full size image is already small enough to be a thumbnail
				// Set this as a thumbnail to disable future attempts
				fullSize.setThumbnail(true);
				tagger.plugin(url, origin, "_plugin/cache", fullSize, "-_plugin/delta/cache");
				return fetch(url, origin);
			}
			return new ByteArrayInputStream(data);
		} catch (Exception e) {
			tagger.attachError(origin,
				refRepository.findOneByUrlAndOrigin(url, origin).orElseThrow(),
				"Error creating thumbnail", e.getMessage());
			return null;
		}
	}

	@Timed(value = "jasper.proxy")
	public Ref statThumbnail(String url, String origin) {
		var ref = stat(url, origin);
		var config = getCache(ref);
		if (config.isThumbnail()) return ref;
		var thumbnailId = "t_" + config.getId();
		var thumbnailUrl = "cache:" + thumbnailId;
		return stat(thumbnailUrl, origin);
	}

}
