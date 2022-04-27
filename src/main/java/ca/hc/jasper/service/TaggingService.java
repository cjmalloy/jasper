package ca.hc.jasper.service;

import java.util.List;

import ca.hc.jasper.component.Ingest;
import ca.hc.jasper.errors.*;
import ca.hc.jasper.repository.RefRepository;
import ca.hc.jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaggingService {
	private static final Logger logger = LoggerFactory.getLogger(TaggingService.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Ingest ingest;

	@Autowired
	Auth auth;

	@PreAuthorize("@auth.canTag(#tag, #url)")
	public void create(String tag, String url, String origin) {
		if (!origin.isEmpty()) throw new ForeignWriteException();
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException();
		var ref = maybeRef.get();
		if (ref.getTags() != null && ref.getTags().contains(tag)) throw new DuplicateTagException();
		ref.addTags(List.of(tag));
		ingest.update(ref);
	}

	@PreAuthorize("@auth.canTag(#tag, #url)")
	public void delete(String tag, String url, String origin) {
		if (!origin.isEmpty()) throw new ForeignWriteException();
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException();
		var ref = maybeRef.get();
		if (ref.getTags() == null || !ref.getTags().contains(tag)) return;
		ref.getTags().remove(tag);
		ingest.update(ref);
	}
}
