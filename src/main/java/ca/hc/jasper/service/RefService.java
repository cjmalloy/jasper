package ca.hc.jasper.service;

import java.time.Instant;

import ca.hc.jasper.domain.Ref;
import ca.hc.jasper.repository.PluginRepository;
import ca.hc.jasper.repository.RefRepository;
import ca.hc.jasper.repository.filter.RefFilter;
import ca.hc.jasper.security.Auth;
import ca.hc.jasper.service.dto.DtoMapper;
import ca.hc.jasper.service.dto.RefDto;
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
public class RefService {

	@Autowired
	RefRepository refRepository;

	@Autowired
	PluginRepository pluginRepository;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@Autowired
	Validator validator;

	@Autowired
	ObjectMapper objectMapper;

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void create(Ref ref) {
		if (!ref.local()) throw new ForeignWriteException();
		if (refRepository.existsByUrlAndOrigin(ref.getUrl(), ref.getOrigin())) throw new AlreadyExistsException();
		validate(ref);
		ref.setCreated(Instant.now());
		ref.setModified(Instant.now());
		refRepository.save(ref);
	}

	@PostAuthorize("@auth.canReadRef(returnObject)")
	public RefDto get(String url, String origin) {
		var result = refRepository.findOneByUrlAndOrigin(url, origin)
								  .orElseThrow(NotFoundException::new);
		return mapper.domainToDto(result);
	}

	public Page<RefDto> page(RefFilter filter, Pageable pageable) {
		return refRepository
			.findAll(
				auth.<Ref>refReadSpec()
					.and(filter.spec()),
				pageable)
			.map(mapper::domainToDto);
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void update(Ref ref) {
		if (!ref.local()) throw new ForeignWriteException();
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		var existing = maybeExisting.get();
		if (!ref.getModified().equals(existing.getModified())) throw new ModifiedException();
		ref.addTags(auth.hiddenTags(existing.getTags()));
		validate(ref);
		ref.setModified(Instant.now());
		refRepository.save(ref);
	}

	@PreAuthorize("@auth.canWriteRef(#url)")
	public void delete(String url) {
		try {
			refRepository.deleteByUrlAndOrigin(url, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void validate(Ref ref) {
		validatePlugins(ref);
		validateSources(ref);
		validateResponses(ref);
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void validatePlugins(Ref ref) {
		if (ref.getTags() == null) return;
		for (var tag : ref.getTags()) {
			var maybePlugin = pluginRepository.findByTagAndSchemaIsNotNull(tag);
			if (maybePlugin.isEmpty()) continue;
			var plugin = maybePlugin.get();
			if (ref.getPlugins() == null) throw new InvalidPluginException(tag);
			if (!ref.getPlugins().has(tag)) throw new InvalidPluginException(tag);
			var pluginData = new JacksonAdapter(ref.getPlugins().get(tag));
			var schema = objectMapper.convertValue(plugin.getSchema(), Schema.class);
			try {
				if (validator.validate(schema, pluginData).size() > 0) throw new InvalidPluginException(tag);
			} catch (MaxDepthExceededException e) {
				throw new InvalidPluginException(tag);
			}
		}
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void validateSources(Ref ref) {
		if (ref.getSources() == null) return;
		for (var sourceUrl : ref.getSources()) {
			// TODO: should we only validate local refs?
			var sources = refRepository.findAllByUrlAndPublishedAfter(sourceUrl, ref.getPublished());
			for (var source : sources) {
				throw new PublishDateException(ref.getUrl(), source.getUrl());
			}
		}
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void validateResponses(Ref ref) {
		// TODO: should we only validate local refs?
		var responses = refRepository.findAllResponsesPublishedBefore(ref.getUrl(), ref.getPublished());
		for (var response : responses) {
			throw new PublishDateException(response.getUrl(), ref.getUrl());
		}
	}
}
