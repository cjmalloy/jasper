package jasper.service;

import io.micrometer.core.annotation.Timed;
import jasper.component.IngestTemplate;
import jasper.domain.Template;
import jasper.errors.NotFoundException;
import jasper.repository.TemplateRepository;
import jasper.repository.filter.TemplateFilter;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.TemplateDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TemplateService {

	@Autowired
	TemplateRepository templateRepository;

	@Autowired
	IngestTemplate ingest;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@PreAuthorize("@auth.local(#template.getOrigin()) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public Instant create(Template template) {
		ingest.create(template);
		return template.getModified();
	}

	@PreAuthorize("@auth.local(#template.getOrigin()) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public void push(Template template) {
		ingest.push(template);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadTag(#qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public TemplateDto get(String qualifiedTag) {
		return templateRepository.findOneByQualifiedTag(qualifiedTag)
			.map(mapper::domainToDto)
			.orElseThrow(() -> new NotFoundException("Template " + qualifiedTag));
	}

	@Transactional(readOnly = true)
	@PreAuthorize( "@auth.hasRole('VIEWER')")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public Instant cursor(String origin) {
		return templateRepository.getCursor(origin);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadQuery(#filter)")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public Page<TemplateDto> page(TemplateFilter filter, Pageable pageable) {
		return templateRepository.findAll(
			auth.<Template>tagReadSpec()
				.and(filter.spec()),
			pageable)
			.map(mapper::domainToDto);
	}

	@PreAuthorize("@auth.local(#template.getOrigin()) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public Instant update(Template template) {
		ingest.update(template);
		return template.getModified();
	}

	@Transactional
	@PreAuthorize("@auth.subOrigin(#qualifiedTag) and @auth.hasRole('ADMIN')")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public void delete(String qualifiedTag) {
		try {
			ingest.delete(qualifiedTag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
