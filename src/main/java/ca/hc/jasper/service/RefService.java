package ca.hc.jasper.service;

import static ca.hc.jasper.repository.spec.RefSpec.readAccess;

import java.time.Instant;

import ca.hc.jasper.component.UserManager;
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

@Service
public class RefService {

	@Autowired
	RefRepository refRepository;

	@Autowired
	UserManager userManager;

	@PreAuthorize("hasPermission(#ref, create)")
	public void create(Ref ref) {
		if (!ref.local()) throw new ForeignWriteException();
		if (refRepository.existsByUrlAndOrigin(ref.getUrl(), ref.getOrigin())) throw new AlreadyExistsException();
		ref.setCreated(Instant.now());
		ref.setModified(Instant.now());
		refRepository.save(ref);
	}

	@PostAuthorize("hasPermission(returnObject, read)")
	public Ref get(String url, String origin) {
		return refRepository.findOneByUrlAndOrigin(url, origin).orElseThrow(NotFoundException::new);
	}

	public Page<Ref> page(RefFilter filter, Pageable pageable) {
		return refRepository.findAll(
			filter.spec().and(
				readAccess(userManager.getReadAccess())),
			pageable);
	}

	@PreAuthorize("hasPermission(#ref.url, 'Ref', write)")
	public void update(Ref ref) {
		if (!ref.local()) throw new ForeignWriteException();
		if (!refRepository.existsByUrlAndOrigin(ref.getUrl(), ref.getOrigin())) throw new NotFoundException();
		ref.setModified(Instant.now());
		refRepository.save(ref);
	}

	@PreAuthorize("hasPermission(#url, 'Ref', delete)")
	public void delete(String url) {
		try {
			refRepository.deleteByUrlAndOrigin(url, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
