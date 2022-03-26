package ca.hc.jasper.service;

import ca.hc.jasper.domain.Tag;
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
		if (tagRepository.existsByTagAndOrigin(tag.getTag(), tag.getOrigin())) throw new AlreadyExistsException();
		tagRepository.save(tag);
	}

	public Tag get(String tag, String origin) {
		return tagRepository.findOneByTagAndOrigin(tag, origin).orElseThrow(NotFoundException::new);
	}

	public Page<Tag> page(Pageable pageable) {
		return tagRepository.findAll(pageable);
	}

	public void update(Tag tag) {
		if (!tag.local()) throw new ForeignWriteException();
		if (!tagRepository.existsByTagAndOrigin(tag.getTag(), tag.getOrigin())) throw new NotFoundException();
		tagRepository.save(tag);
	}

	public void delete(String tag) {
		try {
			tagRepository.deleteByTagAndOrigin(tag, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
