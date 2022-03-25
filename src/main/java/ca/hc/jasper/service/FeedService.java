package ca.hc.jasper.service;

import ca.hc.jasper.domain.Feed;
import ca.hc.jasper.repository.FeedRepository;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
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
		feedRepository.deleteById(origin);
	}
}
