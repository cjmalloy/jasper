package jasper.service;

import jasper.component.Ingest;
import jasper.errors.DuplicateTagException;
import jasper.errors.ForeignWriteException;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
		if (!origin.isEmpty()) throw new ForeignWriteException(origin);
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException("Ref " + origin + " " + url);
		var ref = maybeRef.get();
		if (ref.getTags() != null && ref.getTags().contains(tag)) throw new DuplicateTagException(tag);
		ref.addTags(List.of(tag));
		ingest.update(ref);
	}

	@PreAuthorize("@auth.canTag(#tag, #url)")
	public void delete(String tag, String url, String origin) {
		if (!origin.isEmpty()) throw new ForeignWriteException(origin);
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException("Ref " + origin + " " + url);
		var ref = maybeRef.get();
		if (ref.getTags() == null || !ref.getTags().contains(tag)) return;
		ref.getTags().remove(tag);
		ingest.update(ref);
	}
}
