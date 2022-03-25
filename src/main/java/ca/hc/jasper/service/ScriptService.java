package ca.hc.jasper.service;

import ca.hc.jasper.domain.Script;
import ca.hc.jasper.repository.ScriptRepository;
import ca.hc.jasper.service.errors.AlreadyExistsException;
import ca.hc.jasper.service.errors.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ScriptService {

	@Autowired
	ScriptRepository scriptRepository;

	public void create(Script script) {
		if (scriptRepository.existsById(script.getTag())) throw new AlreadyExistsException();
		scriptRepository.save(script);
	}

	public Script get(String tag) {
		return scriptRepository.findById(tag).orElseThrow(NotFoundException::new);
	}

	public void update(Script script) {
		if (!scriptRepository.existsById(script.getTag())) throw new NotFoundException();
		scriptRepository.save(script);
	}

	public void delete(String tag) {
		scriptRepository.deleteById(tag);
	}
}
