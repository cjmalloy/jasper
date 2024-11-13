package jasper.service;

import io.micrometer.core.annotation.Timed;
import jasper.component.FileCache;
import jasper.component.Proxy;
import jasper.errors.NotFoundException;
import jasper.plugin.Cache;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.RefDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

import static jasper.security.AuthoritiesConstants.USER;

@Service
public class ProxyService {

	@Autowired
	RefRepository refRepository;

	@Autowired
	Auth auth;

	@Autowired
	Optional<FileCache> fileCache;

	@Autowired
	Proxy proxy;

	@Autowired
	DtoMapper mapper;

	@PreAuthorize("@auth.hasRole('USER') && @auth.subOrigin(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public void preFetch(String url, String origin) {
		if (fileCache.isEmpty()) throw new NotFoundException("No file cache");
		fileCache.get().preFetch(url, origin);
	}

	@PreAuthorize("@auth.hasRole('USER') && @auth.subOrigin(origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public void refresh(String url, String origin) throws IOException {
		if (fileCache.isEmpty()) throw new NotFoundException("No file cache");
		fileCache.get().refresh(url, origin);
	}

	@PreAuthorize("@auth.subOrigin(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public void fetchIfExists(String url, String origin, OutputStream os) {
		if (fileCache.isEmpty()) throw new NotFoundException("No file cache");
		if (!refRepository.existsByUrlAndOrigin(url, origin)) throw new NotFoundException("Cache not found");
		fileCache.get().fetch(url, origin, os);
	}

	@PreAuthorize("@auth.subOrigin(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public Cache fetch(String url, String origin) {
		// Only require role for new scrapes
		if (!url.startsWith("cache:") && !auth.hasRole(USER) && !refRepository.existsByUrlAndOrigin(url, origin)) throw new AccessDeniedException("Requires USER role to scrape.");
		return proxy.fetch(url, origin);
	}

	@PreAuthorize("@auth.subOrigin(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public Cache fetch(String url, String origin, boolean thumbnail, OutputStream os) {
		// Only require role for new scrapes
		if (!url.startsWith("cache:") && !auth.hasRole(USER) && !refRepository.existsByUrlAndOrigin(url, origin)) throw new AccessDeniedException("Requires USER role to scrape.");
		return thumbnail
			? proxy.fetchThumbnail(url, origin, os)
			: proxy.fetch(url, origin, os);
	}

	@PreAuthorize("@auth.hasRole('USER') && @auth.subOrigin(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public RefDto save(String origin, InputStream in, String mime) throws IOException {
		if (fileCache.isEmpty()) throw new NotFoundException("No file cache");
		return mapper.domainToDto(fileCache.get().save(origin, in, mime, auth.getUserTag() == null ? null : auth.getUserTag().tag));
	}

	@PreAuthorize("@auth.hasRole('MOD') && @auth.subOrigin(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public void push(String url, String origin, InputStream in) throws IOException {
		if (fileCache.isEmpty()) throw new NotFoundException("No file cache");
		fileCache.get().push(url, origin, in);
	}

	@PreAuthorize("@auth.hasRole('MOD') && @auth.subOrigin(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public void clearDeleted(String origin) {
		if (fileCache.isEmpty()) return;
		fileCache.get().clearDeleted(origin);
	}
}
