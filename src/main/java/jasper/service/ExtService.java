package jasper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import io.micrometer.core.annotation.Timed;
import jasper.component.Validate;
import jasper.domain.Ext;
import jasper.errors.AlreadyExistsException;
import jasper.errors.DuplicateModifiedDateException;
import jasper.errors.InvalidPatchException;
import jasper.errors.InvalidTemplateException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.ExtRepository;
import jasper.repository.filter.TagFilter;
import jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static jasper.repository.spec.QualifiedTag.selector;

@Service
public class ExtService {
	private static final Logger logger = LoggerFactory.getLogger(ExtService.class);

	@Autowired
	ExtRepository extRepository;

	@Autowired
	Auth auth;

	@Autowired
	Validate validate;

	@Autowired
	ObjectMapper objectMapper;

	@PreAuthorize("@auth.canWriteTag(#ext.qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "ext"}, histogram = true)
	public void create(Ext ext) {
		if (extRepository.existsByQualifiedTag(ext.getQualifiedTag())) throw new AlreadyExistsException();
		validate.ext(ext);
		ext.setModified(Instant.now());
		try {
			extRepository.save(ext);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
	}

	@PreAuthorize("@auth.canWriteTag(#ext.qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "ext"}, histogram = true)
	public void push(Ext ext) {
		validate.ext(ext);
		try {
			extRepository.save(ext);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadTag(#qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "ext"}, histogram = true)
	public Ext get(String qualifiedTag) {
		return extRepository.findOneByQualifiedTag(qualifiedTag)
							.orElseThrow(() -> new NotFoundException("Ext " + qualifiedTag));
	}

	@Transactional(readOnly = true)
	@PostAuthorize("@auth.hasRole('VIEWER')")
	@Timed(value = "jasper.service", extraTags = {"service", "ext"}, histogram = true)
	public Instant cursor(String origin) {
		return extRepository.getCursor(origin);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadQuery(#filter)")
	@Timed(value = "jasper.service", extraTags = {"service", "ext"}, histogram = true)
	public Page<Ext> page(TagFilter filter, Pageable pageable) {
		return extRepository
			.findAll(
				auth.<Ext>tagReadSpec()
					.and(filter.spec()),
				pageable);
	}

	@PreAuthorize("@auth.canWriteTag(#ext.qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "ext"}, histogram = true)
	public void update(Ext ext) {
		var maybeExisting = extRepository.findOneByQualifiedTag(ext.getQualifiedTag());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ext " + ext.getQualifiedTag());
		var existing = maybeExisting.get();
		if (!ext.getModified().truncatedTo(ChronoUnit.SECONDS).equals(existing.getModified().truncatedTo(ChronoUnit.SECONDS))) throw new ModifiedException("Ext " + ext.getQualifiedTag());
		validate.ext(ext);
		ext.setModified(Instant.now());
		try {
			extRepository.save(ext);
		} catch (DataIntegrityViolationException e) {
			throw new DuplicateModifiedDateException();
		}
	}

	@PreAuthorize("@auth.canWriteTag(#qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "ext"}, histogram = true)
	public void patch(String qualifiedTag, JsonPatch patch) {
		var created = false;
		var ext = extRepository.findOneByQualifiedTag(qualifiedTag).orElse(null);
		if (ext == null) {
			created = true;
			ext = new Ext();
			var qt = selector(qualifiedTag);
			ext.setTag(qt.tag);
			ext.setOrigin(qt.origin);
		}
		if (ext.getConfig() == null) {
			ext.setConfig(validate.templateDefaults(qualifiedTag));
		}
		try {
			var patched = patch.apply(objectMapper.convertValue(ext, JsonNode.class));
			var updated = objectMapper.treeToValue(patched, Ext.class);
			// @PreAuthorize annotations are not triggered for calls within the same class
			if (!auth.canWriteTag(updated.getQualifiedTag())) throw new AccessDeniedException("Can't add new tags");
			if (created) {
				create(updated);
			} else {
				update(updated);
			}
		} catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidPatchException("Ext " + qualifiedTag, e);
		}
	}

	@Transactional
	@PreAuthorize("@auth.sysMod() or @auth.canWriteTag(#qualifiedTag)")
	@Timed(value = "jasper.service", extraTags = {"service", "ext"}, histogram = true)
	public void delete(String qualifiedTag) {
		try {
			extRepository.deleteByQualifiedTag(qualifiedTag);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}

	private <T extends JsonNode> T merge(T a, JsonNode b) {
		try {
			return objectMapper.updateValue(a, b);
		} catch (JsonMappingException e) {
			throw new InvalidTemplateException("Merging Template schemas", e);
		}
	}
}
