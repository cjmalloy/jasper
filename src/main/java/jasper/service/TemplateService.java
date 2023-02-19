package jasper.service;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Template;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.TemplateRepository;
import jasper.repository.filter.TemplateFilter;
import jasper.security.Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class TemplateService {

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	Auth auth;

	@PreAuthorize("@auth.local(#template.getOrigin()) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public void create(Template template) {
		if (templateRepository.existsByQualifiedTag(template.getQualifiedTag())) throw new AlreadyExistsException();
		template.setModified(Instant.now());
		try {
			templateRepository.save(template);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
	}

	@PreAuthorize("@auth.local(#template.getOrigin()) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public void push(Template template) {
		try {
			templateRepository.save(template);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadTag(#qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public Template get(String qualifiedTag) {
		return templateRepository.findFirstByQualifiedTagOrderByModifiedDesc(qualifiedTag)
								 .orElseThrow(() -> new NotFoundException("Template " + qualifiedTag));
	}

	@Transactional(readOnly = true)
	@PostAuthorize("@auth.hasRole('VIEWER')")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public Instant cursor(String origin) {
		return templateRepository.getCursor(origin);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadQuery(#filter)")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public Page<Template> page(TemplateFilter filter, Pageable pageable) {
		return templateRepository.findAll(
			auth.<Template>tagReadSpec()
				.and(filter.spec()),
			pageable);
	}

	@PreAuthorize("@auth.local(#template.getOrigin()) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public void update(Template template) {
		var maybeExisting = templateRepository.findFirstByQualifiedTagOrderByModifiedDesc(template.getQualifiedTag());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Template "+ template.getQualifiedTag());
		var existing = maybeExisting.get();
		if (!template.getModified().truncatedTo(ChronoUnit.SECONDS).equals(existing.getModified().truncatedTo(ChronoUnit.SECONDS))) throw new ModifiedException("Template");
		template.setModified(Instant.now());
		try {
			templateRepository.save(template);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
	}

	@Transactional
	@PreAuthorize("@auth.sysAdmin() or @auth.local(#qualifiedTag) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public void delete(String qualifiedTag) {
		try {
			templateRepository.deleteByQualifiedTag(qualifiedTag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
