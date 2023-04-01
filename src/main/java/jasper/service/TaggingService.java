package jasper.service;

import io.micrometer.core.annotation.Timed;
import jasper.component.Ingest;
import jasper.domain.Ref;
import jasper.errors.DuplicateTagException;
import jasper.errors.NotFoundException;
import jasper.repository.RefRepository;
import jasper.security.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static jasper.domain.proj.Tag.urlForUser;

@Service
public class TaggingService {
	private static final Logger logger = LoggerFactory.getLogger(TaggingService.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Ingest ingest;

	@Autowired
	Auth auth;

	@PreAuthorize("@auth.canTag(#tag, #url, #origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void create(String tag, String url, String origin) {
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException("Ref " + origin + " " + url);
		var ref = maybeRef.get();
		if (ref.getTags() != null && ref.getTags().contains(tag)) throw new DuplicateTagException(tag);
		ref.addTags(List.of(tag));
		ingest.update(ref, false);
	}

	@PreAuthorize("@auth.canTag(#tag, #url, #origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void delete(String tag, String url, String origin) {
		if (tag.equals("locked")) {
			throw new AccessDeniedException("Cannot unlock Ref");
		}
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException("Ref " + origin + " " + url);
		var ref = maybeRef.get();
		if (ref.getTags() == null || !ref.getTags().contains(tag)) return;
		ref.removePrefixTags();
		ref.getTags().remove(tag);
		ingest.update(ref, false);
	}

	@PreAuthorize("@auth.canTagAll(@auth.tagPatch(#tags), #url, #origin)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void tag(List<String> tags, String url, String origin) {
		if (tags.contains("-locked")) {
			throw new AccessDeniedException("Cannot unlock Ref");
		}
		var maybeRef = refRepository.findOneByUrlAndOrigin(url, origin);
		if (maybeRef.isEmpty()) throw new NotFoundException("Ref " + origin + " " + url);
		var ref = maybeRef.get();
		ref.removePrefixTags();
		ref.addTags(tags);
		ingest.update(ref, false);
	}

	@PreAuthorize("@auth.hasRole('USER') and @auth.canAddTag(#tag)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void createResponse(String tag, String url) {
		var ref = getResponseRef(tag);
		if (ref.getSources() == null) {
			ref.setSources(new ArrayList<>());
		}
		if (!ref.getSources().contains(url)) {
			ref.getSources().add(url);
		}
		ingest.update(ref, false);
	}

	@PreAuthorize("@auth.hasRole('USER') and @auth.canAddTag(#tag)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void deleteResponse(String tag, String url) {
		var ref = getResponseRef(tag);
		if (ref.getSources() == null) {
			ref.setSources(new ArrayList<>());
		}
		ref.getSources().remove(url);
		ingest.update(ref, false);
	}

	@PreAuthorize("@auth.hasRole('USER') and@auth.canAddTags(@auth.tagPatch(#tags))")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void respond(List<String> tags, String url) {
		for (var tag : tags) {
			if (tag.startsWith("-")) {
				deleteResponse(tag.substring(1), url);
			} else {
				createResponse(tag, url);
			}
		}
	}

	private Ref getResponseRef(String tag) {
		var url = urlForUser(tag, auth.getUserTag().toString());
		return refRepository.findOneByUrlAndOrigin(url, auth.getOrigin()).map(ref -> {
				ref.setTags(new ArrayList<>(List.of("internal", auth.getUserTag().tag, tag)));
				return ref;
			})
			.orElseGet(() -> {
				var ref = new Ref();
				ref.setUrl(url);
				ref.setOrigin(auth.getOrigin());
				ref.setTags(new ArrayList<>(List.of("internal", auth.getUserTag().tag, tag)));
				ingest.ingest(ref, false);
				return ref;
			});
	}
}
