package ca.hc.jasper.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import ca.hc.jasper.domain.Template;
import ca.hc.jasper.repository.TemplateRepository;
import ca.hc.jasper.repository.filter.TemplateFilter;
import ca.hc.jasper.security.Auth;
import ca.hc.jasper.service.errors.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
		if (templateRepository.existsByQualifiedPrefix(template.getQualifiedPrefix())) throw new AlreadyExistsException();
		templateRepository.save(template);
	}

	@PreAuthorize("@auth.canReadTag(#tag)")
	public Template get(String tag) {
		return templateRepository.findOneByQualifiedPrefix(tag)
								 .orElseThrow(NotFoundException::new);
	}

	@PreAuthorize("@auth.canReadQuery(#filter)")
	public Page<Template> page(TemplateFilter filter, Pageable pageable) {
		return templateRepository.findAll(filter.spec(), pageable);
	}

	@PreAuthorize("@auth.canWriteTag(#template.qualifiedTag)")
	public void update(Template template) {
		var maybeExisting = templateRepository.findOneByQualifiedPrefix(template.getQualifiedPrefix());
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		var existing = maybeExisting.get();
		if (!template.getModified().truncatedTo(ChronoUnit.SECONDS).equals(existing.getModified().truncatedTo(ChronoUnit.SECONDS))) throw new ModifiedException();
		template.setModified(Instant.now());
		templateRepository.save(template);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void delete(String tag) {
		try {
			templateRepository.deleteByQualifiedPrefix(tag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
