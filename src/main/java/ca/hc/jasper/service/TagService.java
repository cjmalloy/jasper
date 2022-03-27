package ca.hc.jasper.service;

import ca.hc.jasper.component.Auth;
import ca.hc.jasper.domain.Tag;
import ca.hc.jasper.repository.TagRepository;
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
public class TagService {

	@Autowired
	TagRepository tagRepository;

	@Autowired
	Auth auth;

	@PreAuthorize("hasRole('MOD')")
	public void create(Tag tag) {
		if (!tag.local()) throw new ForeignWriteException();
		if (tagRepository.existsByTagAndOrigin(tag.getTag(), tag.getOrigin())) throw new AlreadyExistsException();
		tagRepository.save(tag);
	}

	@PostAuthorize("@auth.canReadTag(returnObject)")
	public Tag get(String tag, String origin) {
		return tagRepository.findOneByTagAndOrigin(tag, origin)
							.orElseThrow(NotFoundException::new);
	}

	public Page<Tag> page(Pageable pageable) {
		return tagRepository.findAll(
			auth.tagReadSpec(),
			pageable);
	}

	@PreAuthorize("@auth.canWriteTag(#tag.tag)")
	public void update(Tag tag) {
		if (!tag.local()) throw new ForeignWriteException();
		if (!tagRepository.existsByTagAndOrigin(tag.getTag(), tag.getOrigin())) throw new NotFoundException();
		tagRepository.save(tag);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void delete(String tag) {
		try {
			tagRepository.deleteByTagAndOrigin(tag, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
