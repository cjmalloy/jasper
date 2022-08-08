package jasper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import jasper.component.Ingest;
import jasper.domain.Ref;
import jasper.errors.ForeignWriteException;
import jasper.errors.InvalidPatchException;
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

import java.time.temporal.ChronoUnit;

import static jasper.repository.spec.OriginSpec.isOrigin;
import static jasper.repository.spec.RefSpec.isUrl;

@Service
@Transactional
public class RefService {
	private static final Logger logger = LoggerFactory.getLogger(RefService.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Ingest ingest;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@Autowired
	ObjectMapper objectMapper;

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void create(Ref ref) {
		if (!ref.local()) throw new ForeignWriteException(ref.getOrigin());
		ingest.ingest(ref);
	}

	@Transactional(readOnly = true)
	@PostAuthorize("@auth.canReadRef(returnObject)")
	public RefDto get(String url, String origin) {
		var result = refRepository.findOneByUrlAndOrigin(url, origin)
			.or(() -> refRepository.findOne(isUrl(url).and(isOrigin(origin))))
			.orElseThrow(() -> new NotFoundException("Ref " + origin + " " + url));
		return mapper.domainToDto(result);
	}

	@Transactional(readOnly = true)
	public boolean exists(String url, String origin) {
		return refRepository.existsByUrlAndOrigin(url, origin);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadQuery(#filter)")
	public Page<RefDto> page(RefFilter filter, Pageable pageable) {
		return refRepository
			.findAll(
				auth.<Ref>refReadSpec()
					.and(filter.spec()),
				pageable)
			.map(mapper::domainToDto);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("@auth.canReadQuery(#filter)")
	public long count(RefFilter filter) {
		return refRepository
			.count(
				auth.<Ref>refReadSpec()
					.and(filter.spec()));
	}

	@PreAuthorize("@auth.canWriteRef(#ref)")
	public void update(Ref ref) {
		if (!ref.local()) throw new ForeignWriteException(ref.getOrigin());
		var maybeExisting = refRepository.findOneByUrlAndOrigin(ref.getUrl(), ref.getOrigin());
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ref " + ref.getOrigin() + " " + ref.getUrl());
		var existing = maybeExisting.get();
		if (!ref.getModified().truncatedTo(ChronoUnit.SECONDS).equals(existing.getModified().truncatedTo(ChronoUnit.SECONDS))) throw new ModifiedException("Ref");
		ref.addTags(auth.hiddenTags(existing.getTags()));
		ingest.update(ref);
	}

	@PreAuthorize("@auth.canWriteRef(#url)")
	public void patch(String url, String origin, JsonPatch patch) {
		var maybeExisting = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeExisting.isEmpty()) throw new NotFoundException("Ref " + origin + " " + url);
		try {
			var patched = patch.apply(objectMapper.convertValue(maybeExisting.get(), JsonNode.class));
			var updated = objectMapper.treeToValue(patched, Ref.class);
			// @PreAuthorize annotations are not triggered for calls within the same class
			if (!auth.canWriteRef(updated)) throw new AccessDeniedException("Can't add new tags");
			update(updated);
		} catch (JsonPatchException | JsonProcessingException e) {
			throw new InvalidPatchException("Ref " + origin + " " + url, e);
		}
	}

	@PreAuthorize("@auth.canWriteRef(#url)")
	public void delete(String url) {
		try {
			ingest.delete(url, "");
		} catch (EmptyResultDataAccessException e) {
			// Delete is idempotent
		}
	}
}
