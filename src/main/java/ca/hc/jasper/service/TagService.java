package ca.hc.jasper.service;

import java.time.Instant;

import ca.hc.jasper.domain.Tag;
import ca.hc.jasper.repository.TagRepository;
import ca.hc.jasper.repository.TemplateRepository;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.security.Auth;
import ca.hc.jasper.service.errors.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsontypedef.jtd.*;
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
	TemplateRepository templateRepository;

	@Autowired
	Auth auth;

	@Autowired
	Validator validator;

	@Autowired
	ObjectMapper objectMapper;

	@PreAuthorize("@auth.canWriteTag(#tag.tag)")
	public void create(Tag tag) {
		if (!tag.local()) throw new ForeignWriteException();
		if (tagRepository.existsByTagAndOrigin(tag.getTag(), tag.getOrigin())) throw new AlreadyExistsException();
		validate(tag);
		tag.setModified(Instant.now());
		tagRepository.save(tag);
	}

	@PostAuthorize("@auth.canReadTag(returnObject)")
	public Tag get(String tag, String origin) {
		return tagRepository.findOneByTagAndOrigin(tag, origin)
							.orElseThrow(NotFoundException::new);
	}

	public Page<Tag> page(TagFilter filter, Pageable pageable) {
		return tagRepository.findAll(
			auth.<Tag>tagReadSpec()
				.and(filter.spec()),
			pageable);
	}

	@PreAuthorize("@auth.canWriteTag(#tag.tag)")
	public void update(Tag tag) {
		if (!tag.local()) throw new ForeignWriteException();
		if (!tagRepository.existsByTagAndOrigin(tag.getTag(), tag.getOrigin())) throw new NotFoundException();
		validate(tag);
		tag.setModified(Instant.now());
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

	@PreAuthorize("@auth.canWriteRef(#tag.tag)")
	public void validate(Tag tag) {
		var templates = templateRepository.findAllForTagWithSchema(tag.getTag());
		for (var template : templates) {
			if (tag.getConfig() == null) throw new InvalidTemplateException(template.getTag());
			var tagConfig = new JacksonAdapter(tag.getConfig());
			var schema = objectMapper.convertValue(template.getSchema(), Schema.class);
			try {
				if (validator.validate(schema, tagConfig).size() > 0) throw new InvalidTemplateException(template.getTag());
			} catch (MaxDepthExceededException e) {
				throw new InvalidTemplateException(template.getTag());
			}
		}
	}
}
