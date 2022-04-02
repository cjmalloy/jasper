package ca.hc.jasper.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import ca.hc.jasper.domain.Ext;
import ca.hc.jasper.repository.ExtRepository;
import ca.hc.jasper.repository.TemplateRepository;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.security.Auth;
import ca.hc.jasper.service.errors.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.jsontypedef.jtd.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
		validate(ext, true);
		ext.setModified(Instant.now());
		extRepository.save(ext);
	}

	@PreAuthorize("@auth.canReadTag(#tag)")
	public Ext get(String tag) {
		return extRepository.findOneByQualifiedTag(tag)
							.orElseThrow(NotFoundException::new);
	}

	@PreAuthorize("@auth.canReadQuery(#filter)")
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
		validate(ext, false);
		ext.setModified(Instant.now());
		extRepository.save(ext);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void patch(String tag, JsonPatch patch) {
		var maybeExisting = extRepository.findOneByQualifiedTag(tag);
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		try {
			var patched = patch.apply(objectMapper.convertValue(maybeExisting.get(), JsonNode.class));
			update(objectMapper.treeToValue(patched, Ext.class));
		} catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidPatchException(e);
		}
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
	public void validate(Ext ext, boolean useDefaults) {
		var templates = templateRepository.findAllForTagAndOriginWithSchema(ext.getTag(), ext.getOrigin());
		for (var template : templates) {
			if (ext.getConfig() == null) {
				if (!useDefaults) throw new InvalidTemplateException(template.getTag());
				ext.setConfig(template.getDefaults());
			}
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
