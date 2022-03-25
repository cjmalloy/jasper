package ca.hc.jasper.service;

import java.util.List;

import ca.hc.jasper.domain.Tag;
import ca.hc.jasper.domain.TagId;
import ca.hc.jasper.repository.TagRepository;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TagService {

	@Autowired
	TagRepository tagRepository;

	public void create(Tag tag) {
		if (tag.getOrigin() != null) throw new ForeignWriteException();
		if (tagRepository.existsById(tag.getId())) throw new AlreadyExistsException();
		tagRepository.save(tag);
	}

	public Tag get(TagId id) {
		return tagRepository.findById(id).orElseThrow(NotFoundException::new);
	}

	public List<Tag> getAll(String tag) {
		return tagRepository.findAllByTag(tag);
	}

	public void update(Tag tag) {
		if (tag.getOrigin() != null) throw new ForeignWriteException();
		if (!tagRepository.existsById(tag.getId())) throw new NotFoundException();
		tagRepository.save(tag);
	}

	public void delete(String url) {
		tagRepository.deleteById(new TagId(url, null));
	}
}
