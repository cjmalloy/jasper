package ca.hc.jasper.service;

import java.io.IOException;

import ca.hc.jasper.component.FeedScraper;
import ca.hc.jasper.domain.Feed;
import ca.hc.jasper.errors.*;
import ca.hc.jasper.repository.FeedRepository;
import ca.hc.jasper.repository.filter.RefFilter;
import ca.hc.jasper.security.Auth;
import ca.hc.jasper.service.dto.DtoMapper;
import ca.hc.jasper.service.dto.FeedDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.rometools.rome.io.FeedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FeedService {

	@Autowired
	FeedRepository feedRepository;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	FeedScraper feedScraper;

	@PreAuthorize("hasRole('EDITOR')")
	public void create(Feed feed) {
		if (!feed.local()) throw new ForeignWriteException();
		if (feedRepository.existsByUrlAndOrigin(feed.getUrl(), feed.getOrigin())) throw new AlreadyExistsException();
		feedRepository.save(feed);
	}

	@PostAuthorize("@auth.canReadRef(returnObject)")
	public FeedDto get(String url, String origin) {
		var result = feedRepository.findOneByUrlAndOrigin(url, origin)
								   .orElseThrow(NotFoundException::new);
		return mapper.domainToDto(result);
	}

	@PreAuthorize("@auth.canReadQuery(#filter)")
	public Page<FeedDto> page(RefFilter filter, Pageable pageable) {
		return feedRepository
			.findAll(
				auth.<Feed>refReadSpec()
					.and(filter.feedSpec()),
				pageable)
			.map(mapper::domainToDto);
	}

	@PreAuthorize("hasRole('EDITOR')")
	public void update(Feed feed) {
		if (!feed.local()) throw new ForeignWriteException();
		if (!feedRepository.existsByUrlAndOrigin(feed.getUrl(), feed.getOrigin())) throw new NotFoundException();
		feedRepository.save(feed);
	}

	@PreAuthorize("hasRole('EDITOR')")
	public void patch(String url, String origin, JsonPatch patch) {
		var maybeExisting = feedRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		try {
			var patched = patch.apply(objectMapper.convertValue(maybeExisting.get(), JsonNode.class));
			var updated = objectMapper.treeToValue(patched, Feed.class);
			update(updated);
		} catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidPatchException(e);
		}
	}

	@PreAuthorize("hasRole('EDITOR')")
	public void delete(String url) {
		try {
			feedRepository.deleteByUrlAndOrigin(url, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}

	@PreAuthorize("hasRole('MOD')")
	public void scrape(String url, String origin) throws FeedException, IOException {
		var feed = feedRepository.findOneByUrlAndOrigin(url, origin)
								   .orElseThrow(NotFoundException::new);
		feedScraper.scrape(feed);
	}
}
