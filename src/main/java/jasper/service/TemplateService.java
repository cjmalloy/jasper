package jasper.service;

import jasper.domain.Template;
import jasper.errors.AlreadyExistsException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.TemplateRepository;
import jasper.repository.filter.TemplateFilter;
import jasper.security.Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Transactional
public class TemplateService {

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	Auth auth;

	@PreAuthorize("hasRole('ADMIN')")
	public void create(Template template) {
		if (templateRepository.existsByQualifiedTag(template.getQualifiedTag())) throw new AlreadyExistsException();
		templateRepository.save(template);
	}

	@PreAuthorize("@auth.canReadTag(#tag)")
	public Template get(String tag) {
		return templateRepository.findOneByQualifiedTag(tag)
								 .orElseThrow(() -> new NotFoundException("Template"));
	}

	@PreAuthorize("@auth.canReadTag(#tag)")
	public boolean exists(String tag) {
		return templateRepository.existsByQualifiedTag(tag);
	}

	@PreAuthorize("@auth.canReadQuery(#filter)")
	public Page<Template> page(TemplateFilter filter, Pageable pageable) {
		return templateRepository.findAll(filter.spec(), pageable);
	}

	@PreAuthorize("hasRole('ADMIN')")
	public void update(Template template) {
		var maybeExisting = templateRepository.findOneByQualifiedTag(template.getQualifiedTag());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Template");
		var existing = maybeExisting.get();
		if (!template.getModified().truncatedTo(ChronoUnit.SECONDS).equals(existing.getModified().truncatedTo(ChronoUnit.SECONDS))) throw new ModifiedException("Template");
		template.setModified(Instant.now());
		templateRepository.save(template);
	}

	@PreAuthorize("hasRole('ADMIN')")
	public void delete(String tag) {
		try {
			templateRepository.deleteByQualifiedTag(tag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
