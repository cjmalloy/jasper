package jasper.service;

import io.micrometer.core.annotation.Timed;
import jasper.component.Ingest;
import jasper.component.Tagger;
import jasper.domain.Ref;
import jasper.errors.DuplicateTagException;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import jasper.service.dto.DtoMapper;
import jasper.service.dto.RefDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
public class TaggingService {
	private static final Logger logger = LoggerFactory.getLogger(TaggingService.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Ingest ingest;

	@Autowired
	Tagger tagger;

	@Autowired
	Auth auth;

	@Autowired
	DtoMapper mapper;

	@PreAuthorize("@auth.canTag(#tag, #url, #origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public Instant create(String tag, String url, String origin) {
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException("Ref " + origin + " " + url);
		var ref = maybeRef.get();
		if (ref.hasTag(tag)) throw new DuplicateTagException(tag);
		ref.addTag(tag);
		ingest.update(ref, false);
		return ref.getModified();
	}

	@PreAuthorize("@auth.canWriteRef(#url, #origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public Instant delete(String tag, String url, String origin) {
		if (tag.equals("locked")) {
			throw new AccessDeniedException("Cannot unlock Ref");
		}
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException("Ref " + origin + " " + url);
		var ref = maybeRef.get();
		ref.removePrefixTags();
		if (!ref.hasTag(tag)) return ref.getModified();
		if (ref.hasTag("locked") && ref.hasPlugin(tag)) {
			throw new AccessDeniedException("Cannot untag locked Ref with plugin data");
		}
		ref.removeTag(tag);
		ingest.update(ref, false);
		return ref.getModified();
	}

	@PreAuthorize("@auth.canTagAll(@auth.tagPatch(#tags), #url, #origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public Instant tag(List<String> tags, String url, String origin) {
		if (tags.contains("-locked")) {
			throw new AccessDeniedException("Cannot unlock Ref");
		}
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException("Ref " + origin + " " + url);
		var ref = maybeRef.get();
		ref.removePrefixTags();
		if (ref.hasTag("locked")) {
			for (var t : tags) {
				if (t.startsWith("-") && ref.hasPlugin(t.substring(1))) {
					throw new AccessDeniedException("Cannot untag locked Ref with plugin data");
				}
			}
		}
		ref.addTags(tags);
		ingest.update(ref, false);
		return ref.getModified();
	}

	@PreAuthorize("@auth.hasRole('USER') and @auth.canAddTag(#tag)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public RefDto getResponse(String tag, String url) {
		return mapper.domainToDto(getRef(url, tag));
	}

	@PreAuthorize("@auth.hasRole('USER') and @auth.canAddTag(#tag)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void createResponse(String tag, String url) {
		var ref = getRef(url, tag);
		if (isNotBlank(tag) && !ref.hasTag(tag)) {
			ref.addTag(tag);
			ingest.update(ref, false);
		}
	}

	@PreAuthorize("@auth.hasRole('USER') and @auth.canAddTag(#tag)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void deleteResponse(String tag, String url) {
		var ref = getRef(url, tag);
		ref.removeTag(tag);
		ingest.update(ref, true);
	}

	@PreAuthorize("@auth.hasRole('USER') and @auth.canAddTags(@auth.tagPatch(#tags))")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void respond(List<String> tags, String url) {
		var ref = getRef(url, null);
		for (var tag : tags) ref.addTag(tag);
		ingest.update(ref, true);
	}

	private Ref getRef(String url, String tag) {
		if (isNotBlank(url)) return getResponseRef(url);
		if (isNotBlank(tag)) return getTagRef(tag);
		return getUserRef();
	}

	private Ref getUserRef() {
		return getResponseRef(null);
	}

	private Ref getTagRef(String tag) {
		return getResponseRef("tag:/" + tag);
	}

	private Ref getResponseRef(String url) {
		if (auth.getUserTag() == null || isBlank(auth.getUserTag().tag)) throw new NotFoundException("User URL");
		return tagger.getResponseRef(auth.getUserTag().tag, auth.getOrigin(), url);
	}
}
