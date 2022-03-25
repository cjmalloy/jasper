package ca.hc.jasper.service;

import java.util.List;

import ca.hc.jasper.domain.Tag;
import ca.hc.jasper.domain.TagId;
import ca.hc.jasper.repository.TagRepository;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class TagService {

	@Autowired
	TagRepository tagRepository;

	public void create(Tag tag) {
		if (!tag.local()) throw new ForeignWriteException();
		if (tagRepository.existsById(tag.getId())) throw new AlreadyExistsException();
		tagRepository.save(tag);
	}

	public Tag get(TagId id) {
		return tagRepository.findById(id).orElseThrow(NotFoundException::new);
	}

	public List<Tag> getAllOrigins(String tag) {
		return tagRepository.findAllByTag(tag);
	}

	public Page<Tag> page(Pageable pageable) {
		return tagRepository.findAll(pageable);
	}

	public void update(Tag tag) {
		if (!tag.local()) throw new ForeignWriteException();
		if (!tagRepository.existsById(tag.getId())) throw new NotFoundException();
		tagRepository.save(tag);
	}

	public void delete(String url) {
		try {
			tagRepository.deleteById(new TagId(url, ""));
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
