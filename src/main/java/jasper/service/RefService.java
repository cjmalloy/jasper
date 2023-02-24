package jasper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import io.micrometer.core.annotation.Timed;
import jasper.component.Ingest;
import jasper.component.Validate;
import jasper.config.Props;
import jasper.domain.Ref;
import jasper.errors.InvalidPatchException;
import jasper.errors.MaxSourcesException;
import jasper.errors.ModifiedException;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.repository.filter.RefFilter;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.RefDto;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.isUrl;

@Service
public class RefService {
	private static final Logger logger = LoggerFactory.getLogger(RefService.class);

	@Autowired
	Props props;

	@Autowired
	RefRepository refRepository;

	@Autowired
	Ingest ingest;

	@Autowired
	Auth auth;

	@Autowired
	Validate validate;

	@Autowired
	DtoMapper mapper;

	@Autowired
	ObjectMapper objectMapper;

	@PreAuthorize("@auth.canWriteRef(#ref)")
	@Timed(value = "jasper.service", extraTags = {"service", "ref"}, histogram = true)
	public void create(Ref ref) {
		if (ref.getSources() != null && ref.getSources().size() > props.getMaxSources()) {
			throw new MaxSourcesException(props.getMaxSources(), ref.getSources().size());
		}
		ingest.ingest(ref);
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	@Timed(value = "jasper.service", extraTags = {"service", "ref"}, histogram = true)
	public void push(Ref ref) {
		if (ref.getSources() != null && ref.getSources().size() > props.getMaxSources()) {
			logger.warn("Ignoring max count for push. Max count is set to {}. Ref contains {} sources.", props.getMaxSources(), ref.getSources().size());
		}
		ingest.push(ref);
	}

	@Transactional(readOnly = true)
	@PostAuthorize("@auth.canReadRef(returnObject)")
	@Timed(value = "jasper.service", extraTags = {"service", "ref"}, histogram = true)
	public RefDto get(String url, String origin) {
		var result = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(url, origin)
			.or(() -> refRepository.findOne(isUrl(url).and(isOrigin(origin))))
			.orElseThrow(() -> new NotFoundException("Ref " + origin + " " + url));
		return mapper.domainToDto(result);
	}

	@Transactional(readOnly = true)
	@PostAuthorize("@auth.hasRole('VIEWER')")
	@Timed(value = "jasper.service", extraTags = {"service", "ref"}, histogram = true)
	public Instant cursor(String origin) {
		return refRepository.getCursor(origin);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadQuery(#filter)")
	@Timed(value = "jasper.service", extraTags = {"service", "ref"}, histogram = true)
	public Page<RefDto> page(RefFilter filter, Pageable pageable) {
		return refRepository
			.findAll(
				auth.refReadSpec()
					.and(filter.spec()),
				pageable)
			.map(mapper::domainToDto);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadQuery(#filter)")
	@Timed(value = "jasper.service", extraTags = {"service", "ref"}, histogram = true)
	public long count(RefFilter filter) {
		return refRepository
			.count(
				auth.refReadSpec()
					.and(filter.spec()));
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	@Timed(value = "jasper.service", extraTags = {"service", "ref"}, histogram = true)
	public void update(Ref ref) {
		if (ref.getSources() != null && ref.getSources().size() > props.getMaxSources()) {
			throw new MaxSourcesException(props.getMaxSources(), ref.getSources().size());
		}
		var maybeExisting = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ref " + ref.getOrigin() + " " + ref.getUrl());
		var existing = maybeExisting.get();
		if (!ref.getModified().truncatedTo(ChronoUnit.SECONDS).equals(existing.getModified().truncatedTo(ChronoUnit.SECONDS))) throw new ModifiedException("Ref");
		var hiddenTags = auth.hiddenTags(existing.getTags());
		ref.addTags(hiddenTags);
		ref.addPlugins(hiddenTags, existing.getPlugins());
		ingest.update(ref);
	}

	@PreAuthorize("@auth.canWriteRef(#url, #origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "ref"}, histogram = true)
	public void patch(String url, String origin, JsonPatch patch) {
		var created = false;
		var ref = refRepository.findFirstByUrlAndOriginOrderByModifiedDesc(url, origin).orElse(null);
		if (ref == null) {
			created = true;
			ref = new Ref();
			ref.setUrl(url);
			ref.setOrigin(origin);
		}
		ref.setPlugins(validate.pluginDefaults(ref));
		try {
			var patched = patch.apply(objectMapper.convertValue(ref, JsonNode.class));
			var updated = objectMapper.treeToValue(patched, Ref.class);
			// @PreAuthorize annotations are not triggered for calls within the same class
			if (!auth.canWriteRef(updated)) throw new AccessDeniedException("Can't add new tags");
			if (created) {
				create(updated);
			} else {
				update(updated);
			}
		} catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidPatchException("Ref " + origin + " " + url, e);
		}
	}

	@Transactional
	@PreAuthorize("@auth.sysMod() or @auth.canWriteRef(#url, #origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "ref"}, histogram = true)
	public void delete(String url, String origin) {
		try {
			ingest.delete(url, origin);
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
