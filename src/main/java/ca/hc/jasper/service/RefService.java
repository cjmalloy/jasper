package ca.hc.jasper.service;

import java.time.Instant;
import java.util.List;

import ca.hc.jasper.domain.*;
import ca.hc.jasper.repository.RefRepository;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

@Service
public class RefService {

	@Autowired
	RefRepository refRepository;

	public void create(Ref ref) {
		if (!ref.local()) throw new ForeignWriteException();
		if (refRepository.existsById(ref.getId())) throw new AlreadyExistsException();
		ref.setCreated(Instant.now());
		ref.setModified(Instant.now());
		refRepository.save(ref);
	}

	public Ref get(RefId id) {
		return refRepository.findById(id).orElseThrow(NotFoundException::new);
	}

	public List<Ref> getAllOrigins(String url) {
		return refRepository.findAllByUrl(url);
	}

	public Page<Ref> page(Pageable pageable) {
		return refRepository.findAll(pageable);
	}

	public void update(Ref ref) {
		if (!ref.local()) throw new ForeignWriteException();
		if (!refRepository.existsById(ref.getId())) throw new NotFoundException();
		ref.setModified(Instant.now());
		refRepository.save(ref);
	}

	public void delete(String url) {
		try {
			refRepository.deleteById(new RefId(url, ""));
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
