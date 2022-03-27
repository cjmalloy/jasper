package ca.hc.jasper.service;

import java.time.Instant;

import ca.hc.jasper.security.Auth;
import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.repository.RefRepository;
import ca.hc.jasper.repository.filter.RefFilter;
import ca.hc.jasper.service.errors.*;
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
public class RefService {

	@Autowired
	RefRepository refRepository;

	@Autowired
	Auth auth;

	@PreAuthorize("hasRole('USER')")
	public void create(Ref ref) {
		if (!ref.local()) throw new ForeignWriteException();
		if (refRepository.existsByUrlAndOrigin(ref.getUrl(), ref.getOrigin())) throw new AlreadyExistsException();
		ref.setCreated(Instant.now());
		ref.setModified(Instant.now());
		refRepository.save(ref);
	}

	@PostAuthorize("@auth.canReadRef(returnObject)")
	public Ref get(String url, String origin) {
		return refRepository.findOneByUrlAndOrigin(url, origin)
							.orElseThrow(NotFoundException::new);
	}

	public Page<Ref> page(RefFilter filter, Pageable pageable) {
		return refRepository.findAll(
			filter.spec().and(
				auth.refReadSpec()),
			pageable);
	}

	@PreAuthorize("@auth.canWriteRef(#ref.url)")
	public void update(Ref ref) {
		if (!ref.local()) throw new ForeignWriteException();
		if (!refRepository.existsByUrlAndOrigin(ref.getUrl(), ref.getOrigin())) throw new NotFoundException();
		ref.setModified(Instant.now());
		refRepository.save(ref);
	}

	@PreAuthorize("@auth.canWriteRef(#url)")
	public void delete(String url) {
		try {
			refRepository.deleteByUrlAndOrigin(url, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
