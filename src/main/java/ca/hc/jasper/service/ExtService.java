package ca.hc.jasper.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import ca.hc.jasper.domain.Ext;
import ca.hc.jasper.repository.ExtRepository;
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
public class ExtService {
	private static final Logger logger = LoggerFactory.getLogger(ExtService.class);

	@Autowired
	ExtRepository extRepository;

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	Auth auth;

	@Autowired
	Validator validator;

	@Autowired
	ObjectMapper objectMapper;

	@PreAuthorize("@auth.canWriteTag(#ext.qualifiedTag)")
	public void create(Ext ext) {
		if (extRepository.existsByQualifiedTag(ext.getQualifiedTag())) throw new AlreadyExistsException();
		validate(ext);
		ext.setModified(Instant.now());
		extRepository.save(ext);
	}

	@PostAuthorize("@auth.canReadTag(#tag)")
	public Ext get(String tag) {
		return extRepository.findOneByQualifiedTag(tag)
							.orElseThrow(NotFoundException::new);
	}

	public Page<Ext> page(TagFilter filter, Pageable pageable) {
		return extRepository
			.findAll(
				auth.<Ext>tagReadSpec()
					.and(filter.spec()),
				pageable);
	}

	@PreAuthorize("@auth.canWriteTag(#ext.qualifiedTag)")
	public void update(Ext ext) {
		var maybeExisting = extRepository.findOneByQualifiedTag(ext.getQualifiedTag());
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		var existing = maybeExisting.get();
		if (!ext.getModified().truncatedTo(ChronoUnit.SECONDS).equals(existing.getModified().truncatedTo(ChronoUnit.SECONDS))) throw new ModifiedException();
		validate(ext);
		ext.setModified(Instant.now());
		extRepository.save(ext);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void delete(String tag) {
		try {
			extRepository.deleteByQualifiedTag(tag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}

	@PreAuthorize("@auth.canWriteRef(#ext.qualifiedTag)")
	public void validate(Ext ext) {
		var templates = templateRepository.findAllForTagAndOriginWithSchema(ext.getTag(), ext.getOrigin());
		for (var template : templates) {
			if (ext.getConfig() == null) throw new InvalidTemplateException(template.getTag());
			var tagConfig = new JacksonAdapter(ext.getConfig());
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
