package ca.hc.jasper.service;

import ca.hc.jasper.domain.Script;
import ca.hc.jasper.repository.ScriptRepository;
import ca.hc.jasper.service.errors.AlreadyExistsException;
import ca.hc.jasper.service.errors.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@PreAuthorize("hasRole('ADMIN')")
public class ScriptService {

	@Autowired
	ScriptRepository scriptRepository;

	public void create(Script script) {
		if (scriptRepository.existsById(script.getTag())) throw new AlreadyExistsException();
		scriptRepository.save(script);
	}

	public Script get(String tag) {
		return scriptRepository.findById(tag)
							   .orElseThrow(NotFoundException::new);
	}

	public void update(Script script) {
		if (!scriptRepository.existsById(script.getTag())) throw new NotFoundException();
		scriptRepository.save(script);
	}

	public void delete(String tag) {
		try {
			scriptRepository.deleteById(tag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
