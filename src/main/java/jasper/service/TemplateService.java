package jasper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.Patch;
import io.micrometer.core.annotation.Timed;
import jasper.component.IngestTemplate;
import jasper.domain.Template;
import jasper.errors.InvalidPatchException;
import jasper.errors.NotFoundException;
import jasper.repository.TemplateRepository;
import jasper.repository.filter.TemplateFilter;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.TemplateDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static jasper.domain.proj.Tag.localTag;
import static jasper.domain.proj.Tag.tagOrigin;

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

	@Autowired
	ObjectMapper objectMapper;

	@PreAuthorize("@auth.canEditConfig(#template)")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public Instant create(Template template) {
		ingest.create(template);
		return template.getModified();
	}

	@PreAuthorize("@auth.canEditConfig(#template)")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public void push(Template template) {
		ingest.push(template);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadTag(#qualifiedTag)")
	@Cacheable(value = "template-dto-cache", key = "#qualifiedTag")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public TemplateDto get(String qualifiedTag) {
		return templateRepository.findOneByQualifiedTag(qualifiedTag)
			.map(mapper::domainToDto)
			.orElseThrow(() -> new NotFoundException("Template " + qualifiedTag));
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadOrigin(#origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public Instant cursor(String origin) {
		return templateRepository.getCursor(origin);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.minRole()")
	@Cacheable(value = "template-dto-page-cache", key = "#filter.cacheKey(#pageable)", condition = "@auth.hasRole('MOD')")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public Page<TemplateDto> page(TemplateFilter filter, Pageable pageable) {
		return templateRepository.findAll(
			auth.<Template>tagReadSpec()
				.and(filter.spec()),
			pageable)
			.map(mapper::domainToDto);
	}

	@PreAuthorize("@auth.canEditConfig(#template)")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public Instant update(Template template) {
		ingest.update(template);
		return template.getModified();
	}

	@PreAuthorize("@auth.canEditConfig(#qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public Instant patch(String qualifiedTag, Instant cursor, Patch patch) {
		var created = false;
		var template = templateRepository.findOneByQualifiedTag(qualifiedTag).orElse(null);
		if (template == null) {
			created = true;
			template = new Template();
			template.setTag(localTag(qualifiedTag));
			template.setOrigin(tagOrigin(qualifiedTag));
		}
		try {
			var patched = patch.apply(objectMapper.convertValue(template, JsonNode.class));
			var updated = objectMapper.treeToValue(patched, Template.class);
			if (created) {
				return create(updated);
			} else {
				updated.setModified(cursor);
				return update(updated);
			}
		} catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidPatchException("Template " + qualifiedTag, e);
		}
	}

	@Transactional
	@PreAuthorize("@auth.canEditConfig(#qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "template"}, histogram = true)
	public void delete(String qualifiedTag) {
		try {
			ingest.delete(qualifiedTag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
