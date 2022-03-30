package ca.hc.jasper.service;

import java.time.Instant;

import ca.hc.jasper.domain.Template;
import ca.hc.jasper.repository.TemplateRepository;
import ca.hc.jasper.repository.filter.TagFilter;
import ca.hc.jasper.security.Auth;
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
public class TemplateService {

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	Auth auth;

	@PreAuthorize("hasRole('ADMIN')")
	public void create(Template template) {
		if (!template.local()) throw new ForeignWriteException();
		if (templateRepository.existsByTagAndOrigin(template.getTag(), template.getOrigin())) throw new AlreadyExistsException();
		templateRepository.save(template);
	}

	@PostAuthorize("@auth.canReadTag(returnObject)")
	public Template get(String tag, String origin) {
		return templateRepository.findOneByTagAndOrigin(tag, origin)
								 .orElseThrow(NotFoundException::new);
	}

	public Page<Template> page(TagFilter filter, Pageable pageable) {
		return templateRepository
			.findAll(
				auth.<Template>tagReadSpec()
					.and(filter.spec()),
				pageable);
	}

	@PreAuthorize("@auth.canWriteTag(#template.tag)")
	public void update(Template template) {
		if (!template.local()) throw new ForeignWriteException();
		var maybeExisting = templateRepository.findOneByTagAndOrigin(template.getTag(), template.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		var existing = maybeExisting.get();
		if (!template.getModified().equals(existing.getModified())) throw new ModifiedException();
		template.setModified(Instant.now());
		templateRepository.save(template);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void delete(String tag) {
		try {
			templateRepository.deleteByTagAndOrigin(tag, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
