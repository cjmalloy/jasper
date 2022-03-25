package ca.hc.jasper.service;

import java.util.List;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.domain.RefId;
import ca.hc.jasper.repository.RefRepository;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RefService {

	@Autowired
	RefRepository refRepository;

	public void create(Ref ref) {
		if (ref.getOrigin() != null) throw new ForeignWriteException();
		if (refRepository.existsById(ref.getId())) throw new AlreadyExistsException();
		refRepository.save(ref);
	}

	public Ref get(RefId id) {
		return refRepository.findById(id).orElseThrow(NotFoundException::new);
	}

	public List<Ref> getAll(String url) {
		return refRepository.findAllByUrl(url);
	}

	public void update(Ref ref) {
		if (ref.getOrigin() != null) throw new ForeignWriteException();
		if (!refRepository.existsById(ref.getId())) throw new NotFoundException();
		refRepository.save(ref);
	}

	public void delete(String url) {
		refRepository.deleteById(new RefId(url, null));
	}
}
