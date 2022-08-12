package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.io.FeedException;
import jasper.component.RssParser;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@Transactional
@PreAuthorize("hasRole('MOD')")
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

	public void scrape(String url, String origin) throws FeedException, IOException {
		var feed = refRepository.findOneByUrlAndOrigin(url, origin)
								   .orElseThrow(() -> new NotFoundException("Ref " + origin + " " + url));
		rssParser.scrape(feed);
	}
}
