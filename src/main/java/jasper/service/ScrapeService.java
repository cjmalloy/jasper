package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.io.FeedException;
import io.micrometer.core.annotation.Timed;
import jasper.component.RssParser;
import jasper.component.WebScraper;
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
import java.net.URISyntaxException;

import static jasper.security.AuthoritiesConstants.USER;

@Service
public class ScrapeService {

	@Autowired
	RefRepository refRepository;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	RssParser rssParser;

	@Autowired
	WebScraper webScraper;

	@PreAuthorize( "@auth.hasRole('MOD') and @auth.local(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public void feed(String url, String origin) throws FeedException, IOException {
		var source = refRepository.findOneByUrlAndOrigin(url, origin)
			.orElseThrow(() -> new NotFoundException("Ref " + origin + " " + url));
		rssParser.scrape(source);
	}

	@PreAuthorize( "@auth.hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public RefDto webpage(String url) throws IOException, URISyntaxException {
		var config = webScraper.getConfig(url, auth.getOrigin());
		if (config == null) {
			config = webScraper.getDefaultConfig(auth.getOrigin());
		}
		if (config == null) return null;
		return mapper.domainToDto(webScraper.web(url, auth.getOrigin(), config));
	}

	@PreAuthorize( "@auth.hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public String scrape(String url) throws URISyntaxException, IOException {
		var cache = webScraper.scrape(url, auth.getOrigin());
		if (cache != null) return cache.getMimeType();
		return null;
	}

	@PreAuthorize( "@auth.hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public String refresh(String url) throws IOException {
		var cache = webScraper.refresh(url, auth.getOrigin());
		if (cache != null) return cache.getMimeType();
		return null;
	}

	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public Cache fetch(String url) {
		// Only require role for new scrapes
		if (!refRepository.existsByUrlAndOrigin(url, auth.getOrigin()) && !auth.hasRole(USER)) throw new AccessDeniedException("Requires USER role to scrape.");
		return webScraper.fetch(url, auth.getOrigin());
	}

	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public Cache fetch(String url, OutputStream os) {
		// Only require role for new scrapes
		if (!refRepository.existsByUrlAndOrigin(url, auth.getOrigin()) && !auth.hasRole(USER)) throw new AccessDeniedException("Requires USER role to scrape.");
		return webScraper.fetch(url, auth.getOrigin(), os);
	}

	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public String rss(String url) {
		// Only require role for new scrapes
		if (!refRepository.existsByUrlAndOrigin(url, auth.getOrigin()) && !auth.hasRole(USER)) throw new AccessDeniedException("Requires USER role to scrape.");
		return webScraper.rss(url, auth.getOrigin());
	}

	@PreAuthorize( "@auth.hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public String cache(InputStream in, String mime) throws IOException {
		return "internal:" + webScraper.cache(auth.getOrigin(), in, mime, auth.getUserTag().tag).getId();
	}

	@PreAuthorize( "@auth.hasRole('MOD')")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public void clearDeleted() {
		webScraper.clearDeleted(auth.getOrigin());
	}

	@PreAuthorize("@auth.canAddTag('+plugin/scrape')")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public void clearCache() {
		webScraper.clearCache();
	}
}
