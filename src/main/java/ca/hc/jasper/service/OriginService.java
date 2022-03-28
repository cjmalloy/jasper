package ca.hc.jasper.service;

import java.time.Instant;

import ca.hc.jasper.domain.Origin;
import ca.hc.jasper.repository.OriginRepository;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.security.Auth;
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
public class OriginService {

	@Autowired
	OriginRepository originRepository;

	@Autowired
	Auth auth;

	@PreAuthorize("hasRole('ADMIN')")
	public void create(Origin origin) {
		if (originRepository.existsById(origin.getTag())) throw new AlreadyExistsException();
		originRepository.save(origin);
	}

	@PostAuthorize("@auth.canReadTag(returnObject)")
	public Origin get(String tag) {
		return originRepository.findById(tag)
							   .orElseThrow(NotFoundException::new);
	}

	public Page<Origin> page(TagFilter filter, Pageable pageable) {
		return originRepository.findAll(
			auth.<Origin>tagReadSpec()
				.and(filter.spec()),
			pageable);
	}

	@PreAuthorize("hasRole('ADMIN')")
	public void update(Origin origin) {
		var maybeExisting = originRepository.findById(origin.getTag());
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		var existing = maybeExisting.get();
		if (!origin.getModified().equals(existing.getModified())) throw new ModifiedException();
		origin.setModified(Instant.now());
		originRepository.save(origin);
	}

	@PreAuthorize("hasRole('ADMIN')")
	public void delete(String tag) {
		try {
			originRepository.deleteById(tag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
