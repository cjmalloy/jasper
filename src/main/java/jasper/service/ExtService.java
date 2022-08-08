package jasper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.jsontypedef.jtd.JacksonAdapter;
import com.jsontypedef.jtd.MaxDepthExceededException;
import com.jsontypedef.jtd.Schema;
import com.jsontypedef.jtd.Validator;
import jasper.domain.Ext;
import jasper.domain.Template;
import jasper.errors.AlreadyExistsException;
import jasper.errors.InvalidPatchException;
import jasper.errors.InvalidTemplateException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.ExtRepository;
import jasper.repository.TemplateRepository;
import jasper.repository.filter.TagFilter;
import jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

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

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadTag(#tag)")
	public Ext get(String tag) {
		return extRepository.findOneByQualifiedTag(tag)
							.orElseThrow(() -> new NotFoundException("Ext " + tag));
	}

	@Transactional(readOnly = true)
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
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ext " + ext.getQualifiedTag());
		var existing = maybeExisting.get();
		if (!ext.getModified().truncatedTo(ChronoUnit.SECONDS).equals(existing.getModified().truncatedTo(ChronoUnit.SECONDS))) throw new ModifiedException("Ext " + ext.getQualifiedTag());
		validate(ext, false);
		ext.setModified(Instant.now());
		extRepository.save(ext);
	}

	@PreAuthorize("@auth.canWriteTag(#tag)")
	public void patch(String tag, JsonPatch patch) {
		var maybeExisting = extRepository.findOneByQualifiedTag(tag);
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ext " + tag);
		try {
			var patched = patch.apply(objectMapper.convertValue(maybeExisting.get(), JsonNode.class));
			update(objectMapper.treeToValue(patched, Ext.class));
		} catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidPatchException("Ext " + tag, e);
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

	private <T extends JsonNode> T merge(T a, JsonNode b) {
		try {
			return objectMapper.updateValue(a, b);
		} catch (JsonMappingException e) {
			throw new InvalidTemplateException("Merging Template schemas", e);
		}
	}

	@PreAuthorize("@auth.canWriteTag(#ext.qualifiedTag)")
	public void validate(Ext ext, boolean useDefaults) {
		var templates = templateRepository.findAllForTagAndOriginWithSchema(ext.getTag(), ext.getOrigin());
		if (templates.isEmpty()) {
			// If an ext has no template, or the template is schemaless, no config is allowed
			if (ext.getConfig() != null) throw new InvalidTemplateException(ext.getTag());
			return;
		}
		if (useDefaults && ext.getConfig() == null) {
			var mergedDefaults = templates
				.stream()
				.map(Template::getDefaults)
				.filter(Objects::nonNull)
				.reduce(objectMapper.getNodeFactory().objectNode(), this::merge);
			ext.setConfig(mergedDefaults);
		}
		if (ext.getConfig() == null) throw new InvalidTemplateException(ext.getTag());
		var mergedSchemas = templates
			.stream()
			.map(Template::getSchema)
			.filter(Objects::nonNull)
			.reduce(objectMapper.getNodeFactory().objectNode(), this::merge);
		var tagConfig = new JacksonAdapter(ext.getConfig());
		var schema = objectMapper.convertValue(mergedSchemas, Schema.class);
		try {
			var errors = validator.validate(schema, tagConfig);
			for (var error : errors) {
				logger.debug("Error validating template {}: {}", ext.getTag(), error);
			}
			if (errors.size() > 0) throw new InvalidTemplateException(ext.getTag());
		} catch (MaxDepthExceededException e) {
			throw new InvalidTemplateException(ext.getTag(), e);
		}
	}
}
