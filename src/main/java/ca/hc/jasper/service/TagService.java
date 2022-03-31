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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final Logger logger = LoggerFactory.getLogger(TagService.class);

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

	@PreAuthorize("@auth.canWriteTag(#tag.qualifiedTag)")
	public void create(Tag tag) {
		if (!tag.local()) throw new ForeignWriteException();
		if (tagRepository.existsByQualifiedTag(tag.getQualifiedTag())) throw new AlreadyExistsException();
		validate(tag);
		tag.setModified(Instant.now());
		tagRepository.save(tag);
	}

	@PostAuthorize("@auth.canReadTag(#tag)")
	public Tag get(String tag) {
		return tagRepository.findOneByQualifiedTag(tag)
							.orElseThrow(NotFoundException::new);
	}

	public Page<Tag> page(TagFilter filter, Pageable pageable) {
		return tagRepository
			.findAll(
				auth.<Tag>tagReadSpec()
					.and(filter.spec()),
				pageable);
	}

	@PreAuthorize("@auth.canWriteTag(#tag.qualifiedTag)")
	public void update(Tag tag) {
		if (!tag.local()) throw new ForeignWriteException();
		var maybeExisting = tagRepository.findOneByQualifiedTag(tag.getQualifiedTag());
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		var existing = maybeExisting.get();
		if (!tag.getModified().equals(existing.getModified())) throw new ModifiedException();
		validate(tag);
		tag.setModified(Instant.now());
		tagRepository.save(tag);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void delete(String tag) {
		try {
			tagRepository.deleteByQualifiedTag(tag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}

	@PreAuthorize("@auth.canWriteRef(#tag.qualifiedTag)")
	public void validate(Tag tag) {
		if (!tag.local()) throw new ForeignWriteException();
		var templates = templateRepository.findAllForTagAndOriginWithSchema(tag.getTag(), tag.getOrigin());
		for (var template : templates) {
			if (tag.getConfig() == null) throw new InvalidTemplateException(template.getTag());
			var tagConfig = new JacksonAdapter(tag.getConfig());
			var schema = objectMapper.convertValue(template.getSchema(), Schema.class);
			try {
				var errors = validator.validate(schema, tagConfig);
				for (var error : errors) {
					logger.debug("Error validating template {}: {}", template.getTag(), error);
				}
				if (errors.size() > 0) throw new InvalidTemplateException(template.getTag());
			} catch (MaxDepthExceededException e) {
				throw new InvalidTemplateException(template.getTag(), e);
			}
		}
	}
}
