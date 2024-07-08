package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.io.FeedException;
import io.micrometer.core.annotation.Timed;
import jasper.component.RssParser;
import jasper.component.Scraper;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.RefDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;

@Profile("proxy | file-cache")
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
	Scraper scraper;

	@PreAuthorize("@auth.hasRole('MOD') and @auth.local(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public void feed(String url, String origin) throws FeedException, IOException {
		var source = refRepository.findOneByUrlAndOrigin(url, origin)
			.orElseThrow(() -> new NotFoundException("Ref " + origin + " " + url));
		rssParser.scrape(source);
	}

	@PreAuthorize("@auth.hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public RefDto webpage(String url) throws IOException, URISyntaxException {
		var ref = scraper.web(url, auth.getOrigin());
		if (ref == null) return null;
		return mapper.domainToDto(ref);
	}

	@PreAuthorize("@auth.hasRole('USER')")
	@Timed(value = "jasper.service", extraTags = {"service", "scrape"}, histogram = true)
	public String rss(String url) throws IOException {
		return scraper.rss(url);
	}
}
