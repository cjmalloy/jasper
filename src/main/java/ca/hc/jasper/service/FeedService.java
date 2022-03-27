package ca.hc.jasper.service;

import ca.hc.jasper.domain.Feed;
import ca.hc.jasper.repository.FeedRepository;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("hasRole('MOD')")
public class FeedService {

	@Autowired
	FeedRepository feedRepository;

	public void create(Feed feed) {
		if (feedRepository.existsById(feed.getOrigin())) throw new AlreadyExistsException();
		feedRepository.save(feed);
	}

	public Feed get(String origin) {
		return feedRepository.findById(origin).orElseThrow(NotFoundException::new);
	}

	public void update(Feed feed) {
		if (!feedRepository.existsById(feed.getOrigin())) throw new NotFoundException();
		feedRepository.save(feed);
	}

	public void delete(String origin) {
		try {
			feedRepository.deleteById(origin);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
