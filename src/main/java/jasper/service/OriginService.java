package jasper.service;

import jasper.domain.Origin;
import jasper.errors.AlreadyExistsException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.OriginRepository;
import jasper.repository.filter.OriginFilter;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.OriginNameDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Transactional
public class OriginService {

	@Autowired
	OriginRepository originRepository;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@PreAuthorize("hasRole('ADMIN')")
	public void create(Origin origin) {
		if (originRepository.existsById(origin.getOrigin())) throw new AlreadyExistsException();
		originRepository.save(origin);
	}

	@PreAuthorize("hasRole('ADMIN')")
	public Origin get(String origin) {
		return originRepository.findById(origin)
							   .orElseThrow(() -> new NotFoundException("Origin"));
	}

	@PreAuthorize("hasRole('ADMIN')")
	public Page<Origin> page(OriginFilter filter, Pageable pageable) {
		return originRepository
			.findAll(filter.spec(),
				pageable);
	}

	public Page<OriginNameDto> pageNames(OriginFilter filter, Pageable pageable) {
		return originRepository
			.findAll(filter.spec(),
				pageable)
			.map(mapper::domainToDto);
	}

	@PreAuthorize("hasRole('ADMIN')")
	public void update(Origin origin) {
		var maybeExisting = originRepository.findById(origin.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Origin");
		var existing = maybeExisting.get();
		if (!origin.getModified().truncatedTo(ChronoUnit.SECONDS).equals(existing.getModified().truncatedTo(ChronoUnit.SECONDS))) throw new ModifiedException("Origin");
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
