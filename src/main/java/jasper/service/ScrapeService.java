package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.io.FeedException;
import jasper.component.Replicator;
import jasper.component.RssParser;
import jasper.component.WebScraper;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.RefDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.IOException;

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
	@Autowired
	Replicator replicator;

	@PreAuthorize("hasRole('MOD')")
	public void feed(String url, String origin) throws FeedException, IOException {
		var source = refRepository.findOneByUrlAndOrigin(url, origin)
			.orElseThrow(() -> new NotFoundException("Ref " + origin + " " + url));
		if (source.getTags().contains("+plugin/feed")) {
			rssParser.scrape(source);
		}
		if (source.getTags().contains("+plugin/origin")) {
			replicator.replicate(source);
		}
	}

	@PreAuthorize("hasRole('USER')")
	public RefDto webpage(String url) throws IOException {
		return mapper.domainToDto(webScraper.scrape(url));
	}
}
