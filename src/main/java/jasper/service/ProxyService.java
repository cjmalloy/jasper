package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

	@Autowired
	ObjectMapper objectMapper;

	@PreAuthorize("@auth.hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public void preFetch(String url) {
		if (fileCache.isEmpty()) throw new NotFoundException("No file cache");
		fileCache.get().preFetch(url, auth.getOrigin());
	}

	@PreAuthorize("@auth.hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public void refresh(String url) throws IOException {
		if (fileCache.isEmpty()) throw new NotFoundException("No file cache");
		fileCache.get().refresh(url, auth.getOrigin());
	}

	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public void fetchIfExists(String url, OutputStream os) {
		if (fileCache.isEmpty()) throw new NotFoundException("No file cache");
		if (!refRepository.existsByUrlAndOrigin(url, auth.getOrigin())) throw new NotFoundException("Cache not found");
		fileCache.get().fetch(url, auth.getOrigin(), os);
	}

	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public Cache fetch(String url) {
		// Only require role for new scrapes
		if (!auth.hasRole(USER) && !refRepository.existsByUrlAndOrigin(url, auth.getOrigin())) throw new AccessDeniedException("Requires USER role to scrape.");
		return proxy.fetch(url, auth.getOrigin());
	}

	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public Cache fetch(String url, boolean thumbnail, OutputStream os) {
		// Only require role for new scrapes
		if (!auth.hasRole(USER) && !refRepository.existsByUrlAndOrigin(url, auth.getOrigin())) throw new AccessDeniedException("Requires USER role to scrape.");
		return thumbnail
			? proxy.fetchThumbnail(url, auth.getOrigin(), os)
			: proxy.fetch(url, auth.getOrigin(), os);
	}

	@PreAuthorize("@auth.hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public RefDto save(InputStream in, String mime) throws IOException {
		if (fileCache.isEmpty()) throw new NotFoundException("No file cache");
		return mapper.domainToDto(fileCache.get().save(auth.getOrigin(), in, mime, auth.getUserTag() == null ? null : auth.getUserTag().tag));
	}

	@PreAuthorize("@auth.hasRole('MOD')")
	@Timed(value = "jasper.service", extraTags = {"service", "proxy"}, histogram = true)
	public void clearDeleted() {
		if (fileCache.isEmpty()) return;
		fileCache.get().clearDeleted(auth.getOrigin());
	}
}
