package ca.hc.jasper.service;

import ca.hc.jasper.domain.Feed;
import ca.hc.jasper.repository.FeedRepository;
import ca.hc.jasper.repository.filter.RefFilter;
import ca.hc.jasper.security.Auth;
import ca.hc.jasper.service.dto.DtoMapper;
import ca.hc.jasper.service.dto.FeedDto;
import ca.hc.jasper.service.errors.AlreadyExistsException;
import ca.hc.jasper.service.errors.NotFoundException;
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

	@PreAuthorize("hasRole('MOD')")
	public void create(Feed feed) {
		if (feedRepository.existsById(feed.getUrl())) throw new AlreadyExistsException();
		feedRepository.save(feed);
	}

	@PostAuthorize("@auth.canReadRef(returnObject)")
	public FeedDto get(String url) {
		var result = feedRepository.findById(url)
								   .orElseThrow(NotFoundException::new);
		return mapper.domainToDto(result);
	}

	@PreAuthorize("hasRole('USER')")
	public Page<FeedDto> page(RefFilter filter, Pageable pageable) {
		return feedRepository
			.findAll(
				auth.<Feed>refReadSpec()
					.and(filter.spec()),
				pageable)
			.map(mapper::domainToDto);
	}

	@PreAuthorize("hasRole('MOD')")
	public void update(Feed feed) {
		if (!feedRepository.existsById(feed.getUrl())) throw new NotFoundException();
		feedRepository.save(feed);
	}

	@PreAuthorize("hasRole('MOD')")
	public void delete(String url) {
		try {
			feedRepository.deleteById(url);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
