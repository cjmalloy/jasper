package ca.hc.jasper.service;

import static ca.hc.jasper.repository.spec.OriginSpec.isOrigin;
import static ca.hc.jasper.repository.spec.RefSpec.isUrls;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import ca.hc.jasper.domain.*;
import ca.hc.jasper.repository.PluginRepository;
import ca.hc.jasper.repository.RefRepository;
import ca.hc.jasper.repository.filter.RefFilter;
import ca.hc.jasper.security.Auth;
import ca.hc.jasper.service.dto.DtoMapper;
import ca.hc.jasper.service.dto.RefDto;
import ca.hc.jasper.service.errors.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.jsontypedef.jtd.*;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RefService {
	private static final Logger logger = LoggerFactory.getLogger(RefService.class);

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
		if (refRepository.existsByAlternateUrl(ref.getUrl())) throw new AlreadyExistsException();
		validate(ref, true);
		updateMetadata(ref, null);
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

	public boolean exists(String url, String origin) {
		return refRepository.existsByUrlAndOrigin(url, origin);
	}

	@PreAuthorize("@auth.canReadQuery(#filter)")
	public Page<RefDto> page(RefFilter filter, Pageable pageable) {
		return refRepository
			.findAll(
				auth.<Ref>refReadSpec()
					.and(filter.spec()),
				pageable)
			.map(mapper::domainToDto);
	}

	@PreAuthorize("@auth.canReadQuery(#filter)")
	public long count(RefFilter filter) {
		return refRepository
			.count(
				auth.<Ref>refReadSpec()
					.and(filter.spec()));
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void update(Ref ref) {
		if (!ref.local()) throw new ForeignWriteException();
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		var existing = maybeExisting.get();
		if (!ref.getModified().truncatedTo(ChronoUnit.SECONDS).equals(existing.getModified().truncatedTo(ChronoUnit.SECONDS))) throw new ModifiedException();
		validate(ref, false);
		updateMetadata(ref, existing);
		ref.addTags(auth.hiddenTags(existing.getTags()));
		ref.setModified(Instant.now());
		refRepository.save(ref);
	}

	@PreAuthorize("@auth.canWriteRef(#url)")
	public void patch(String url, String origin, JsonPatch patch) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) throw new NotFoundException();
		try {
			var patched = patch.apply(objectMapper.convertValue(maybeExisting.get(), JsonNode.class));
			var updated = objectMapper.treeToValue(patched, Ref.class);
			// @PreAuthorize annotations are not triggered for calls within the same class
			if (!auth.canWriteRef(updated)) throw new AccessDeniedException("Can't read new tags");
			update(updated);
		} catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidPatchException(e);
		}
	}

	@PreAuthorize("@auth.canWriteRef(#url)")
	public void delete(String url) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, "");
		if (maybeExisting.isEmpty()) return;
		updateMetadata(null, maybeExisting.get());
		try {
			refRepository.deleteByUrlAndOrigin(url, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void validate(Ref ref, boolean useDefaults) {
		validateTags(ref);
		validatePlugins(ref, useDefaults);
		validateSources(ref);
		validateResponses(ref);
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void validateTags(Ref ref) {
		if (ref.getTags() == null) return;
		if (!ref.getTags().stream().allMatch(new HashSet<>()::add)) {
			throw new DuplicateTagException();
		}
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void validatePlugins(Ref ref, boolean useDefaults) {
		if (ref.getTags() == null) return;
		if (ref.getPlugins() != null) {
			// Plugin fields must be tagged
			ref.getPlugins().fieldNames().forEachRemaining(field -> {
				if (field.equals("")) return;
				if (!ref.getTags().contains(field)) {
					throw new InvalidPluginException(field);
				}
			});
		}
		for (var tag : ref.getTags()) {
			validatePlugin(ref, tag, useDefaults);
		}
	}

	private <T extends JsonNode> T merge(T a, JsonNode b) {
		try {
			return objectMapper.updateValue(a, b);
		} catch (JsonMappingException e) {
			throw new InvalidPluginException("Merging", e);
		}
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void validatePlugin(Ref ref, String tag, boolean useDefaults) {
		var plugins = pluginRepository.findAllForTagAndOriginWithSchema(tag, ref.getOrigin());
		if (plugins.isEmpty()) {
			// If a tag has no plugin, or the plugin is schemaless, plugin data is not allowed
			if (ref.getPlugins() != null && ref.getPlugins().has(tag)) throw new InvalidPluginException(tag);
			return;
		}
		if (useDefaults && (ref.getPlugins() == null || !ref.getPlugins().has(tag))) {
			if (ref.getPlugins() == null) {
				ref.setPlugins(objectMapper.getNodeFactory().objectNode());
			}
			var mergedDefaults = plugins
				.stream()
				.map(Plugin::getDefaults)
				.filter(Objects::nonNull)
				.reduce(objectMapper.getNodeFactory().objectNode(), this::merge);
			ref.getPlugins().set(tag, mergedDefaults);
		}
		if (ref.getPlugins() == null) throw new InvalidPluginException(tag);
		if (!ref.getPlugins().has(tag)) throw new InvalidPluginException(tag);
		var mergedSchemas = plugins
			.stream()
			.map(Plugin::getSchema)
			.filter(Objects::nonNull)
			.reduce(objectMapper.getNodeFactory().objectNode(), this::merge);
		var pluginData = new JacksonAdapter(ref.getPlugins().get(tag));
		var schema = objectMapper.convertValue(mergedSchemas, Schema.class);
		try {
			var errors = validator.validate(schema, pluginData);
			for (var error : errors) {
				logger.debug("Error validating plugin {}: {}", tag, error);
			}
			if (errors.size() > 0) throw new InvalidPluginException(tag);
		} catch (MaxDepthExceededException e) {
			throw new InvalidPluginException(tag, e);
		}
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void validateSources(Ref ref) {
		if (ref.getSources() == null) return;
		for (var sourceUrl : ref.getSources()) {
			var sources = refRepository.findAllByUrlAndPublishedGreaterThanEqual(sourceUrl, ref.getPublished());
			for (var source : sources) {
				throw new PublishDateException(ref.getUrl(), source.getUrl());
			}
		}
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void validateResponses(Ref ref) {
		var responses = refRepository.findAllResponsesPublishedBefore(ref.getUrl(), ref.getPublished());
		for (var response : responses) {
			throw new PublishDateException(response, ref.getUrl());
		}
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void updateMetadata(Ref ref, Ref existing) {
		if (ref != null) {
			ref.setMetadata(Metadata
				.builder()
				.responses(refRepository
					.findAllResponsesByOriginWithoutTag(ref.getUrl(), ref.getOrigin(), "internal"))
				.plugins(pluginRepository
					.findAllByGenerateMetadata()
					.stream()
					.map(tag -> new Pair<>(
						tag,
						refRepository.findAllResponsesByOriginWithTag(ref.getUrl(), ref.getOrigin(), tag)))
					.collect(Collectors.toMap(Pair::getValue0, Pair::getValue1)))
				.build()
			);

			if (ref.getTags() != null && !ref.getTags().contains("internal")) {
				// Update sources
				Map<String, String> plugins = pluginRepository
					.findAllByGenerateMetadata()
					.stream()
					.filter(tag -> ref.getTags() != null && ref.getTags().contains(tag))
					.collect(Collectors.toMap(tag -> tag, tag -> ref.getUrl()));
				List<Ref> sources = refRepository.findAll(
					isUrls(ref.getSources())
						.and(isOrigin(ref.getOrigin())));
				for (var source : sources) {
					var old = source.getMetadata();
					if (old == null) {
						logger.warn("Ref missing metadata: {}", ref.getUrl());
						old = Metadata
							.builder()
							.responses(new ArrayList<>())
							.plugins(new HashMap<>())
							.build();
					}
					old.addResponse(ref.getUrl());
					old.addPlugins(plugins);
					source.setMetadata(old);
					refRepository.save(source);
				}
			}
		}

		if (existing != null && existing.getSources() != null) {
			var removedSources = ref == null
				? existing.getSources()
				: existing.getSources()
						.stream()
						.filter(s -> ref.getSources() == null || !ref.getSources().contains(s))
						.toList();
			List<Ref> removed = refRepository.findAll(
				isUrls(removedSources)
					.and(isOrigin(existing.getOrigin())));
			for (var source : removed) {
				var old = source.getMetadata();
				if (old == null) {
					logger.warn("Ref missing metadata: {}", existing.getUrl());
					continue;
				}
				old.remove(existing.getUrl());
				source.setMetadata(old);
				refRepository.save(source);
			}
		}
	}

	@PreAuthorize("hasRole('ADMIN')")
	void backfillMetadata() {
		List<Ref> all = refRepository.findAll(((root, query, cb) -> cb.isNull(root.get(Ref_.metadata))));
		for (var ref : all) {
			updateMetadata(ref, null);
			refRepository.save(ref);
		}
	}
}
