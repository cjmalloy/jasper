package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.io.FeedException;
import io.micrometer.core.annotation.Timed;
import jasper.component.RssParser;
import jasper.component.WebScraper;
import jasper.domain.Web;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.RefDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;

import static jasper.domain.Web.from;
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

	@PreAuthorize("hasRole('MOD') and @auth.local(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public void feed(String url, String origin) throws FeedException, IOException {
		var source = refRepository.findOneByUrlAndOrigin(url, origin)
			.orElseThrow(() -> new NotFoundException("Ref " + origin + " " + url));
		rssParser.scrape(source);
	}

	@PreAuthorize("hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public RefDto webpage(String url) throws IOException, URISyntaxException {
		return mapper.domainToDto(webScraper.web(url));
	}

	@PreAuthorize("hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public void scrape(String url) throws URISyntaxException, IOException {
		webScraper.scrape(url);
	}

	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public Web fetch(String url) {
		// Only require role for new scrapes
		if (!webScraper.exists(url) && !auth.hasRole(USER)) throw new AccessDeniedException("Requires USER role to scrape.");
		return webScraper.fetch(url);
	}

	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public String rss(String url) throws URISyntaxException, IOException {
		// Only require role for new scrapes
		if (!webScraper.exists(url) && !auth.hasRole(USER)) throw new AccessDeniedException("Requires USER role to scrape.");
		return webScraper.rss(url);
	}

	@PreAuthorize("hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public String cache(byte[] data, String mime) {
		return webScraper.cache(from(data, mime)).getUrl();
	}
}
