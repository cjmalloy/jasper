package jasper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import jasper.component.Ingest;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.RefNodeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.isUrl;

@Service
@Transactional(readOnly = true)
public class GraphService {
	private static final Logger logger = LoggerFactory.getLogger(GraphService.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Ingest ingest;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@Autowired
	ObjectMapper objectMapper;

	@PostAuthorize("@auth.canReadRef(returnObject)")
	@Timed(value = "jasper.service", extraTags = {"service", "graph"}, histogram = true)
	public RefNodeDto get(String url, String origin) {
		var result = refRepository.findOneByUrlAndOrigin(url, origin)
				.or(() -> refRepository.findOne(isUrl(url).and(isOrigin(origin))))
				.orElseThrow(() -> new NotFoundException("Ref " + origin + " " + url));
		return mapper.domainToNodeDto(result);
	}
}
