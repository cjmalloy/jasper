package jasper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.rometools.rome.io.FeedException;
import jasper.component.RssParser;
import jasper.domain.Feed;
import jasper.errors.AlreadyExistsException;
import jasper.errors.ForeignWriteException;
import jasper.errors.InvalidPatchException;
import jasper.errors.NotFoundException;
import jasper.repository.FeedRepository;
import jasper.repository.filter.RefFilter;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.FeedDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

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
	RssParser rssParser;

	@PreAuthorize("hasRole('EDITOR')")
	public void create(Feed feed) {
		if (!feed.local()) throw new ForeignWriteException(feed.getOrigin());
		if (feedRepository.existsByUrlAndOrigin(feed.getUrl(), feed.getOrigin())) throw new AlreadyExistsException();
		feedRepository.save(feed);
	}

	@Transactional(readOnly = true)
	@PostAuthorize("@auth.canReadRef(returnObject)")
	public FeedDto get(String url, String origin) {
		var result = feedRepository.findOneByUrlAndOrigin(url, origin)
								   .orElseThrow(() -> new NotFoundException("Feed " + origin + " " + url));
		return mapper.domainToDto(result);
	}

	@Transactional(readOnly = true)
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
		if (!feed.local()) throw new ForeignWriteException(feed.getOrigin());
		if (!feedRepository.existsByUrlAndOrigin(feed.getUrl(), feed.getOrigin())) throw new NotFoundException("Feed " + feed.getOrigin() + " " + feed.getUrl());
		feedRepository.save(feed);
	}

	@PreAuthorize("hasRole('EDITOR')")
	public void patch(String url, String origin, JsonPatch patch) {
		var maybeExisting = feedRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) throw new NotFoundException("Feed " + origin + " " + url);
		try {
			var patched = patch.apply(objectMapper.convertValue(maybeExisting.get(), JsonNode.class));
			var updated = objectMapper.treeToValue(patched, Feed.class);
			update(updated);
		} catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidPatchException("Feed " + origin + " " + url, e);
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
								   .orElseThrow(() -> new NotFoundException("Feed " + origin + " " + url));
		rssParser.scrape(feed);
	}
}
