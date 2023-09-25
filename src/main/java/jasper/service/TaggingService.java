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

import static jasper.domain.proj.Tag.removeTag;
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
		if (ref.getTags().contains("locked") && ref.getPlugins() != null && ref.getPlugins().has(tag)) {
			throw new AccessDeniedException("Cannot untag locked Ref with plugin data");
		}
		ref.removePrefixTags();
		ref.getTags().remove(tag);
		ingest.update(ref, true);
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
		if (ref.getTags().contains("locked")) {
			for (var t : tags) {
				if (t.startsWith("-") && ref.getPlugins() != null && ref.getPlugins().has(t.substring(1))) {
					throw new AccessDeniedException("Cannot untag locked Ref with plugin data");
				}
			}
		}
		ref.addTags(tags);
		ingest.update(ref, true);
	}

	@PreAuthorize( "@auth.hasRole('USER') and @auth.canAddTag(#tag)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void createResponse(String tag, String url) {
		var ref = getResponseRef(url);
		if (!ref.getTags().contains(tag)) {
			ref.getTags().add(tag);
		}
		ingest.update(ref, false);
	}

	@PreAuthorize( "@auth.hasRole('USER') and @auth.canAddTag(#tag)")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void deleteResponse(String tag, String url) {
		var ref = getResponseRef(url);
		removeTag(ref.getTags(), tag);
		ingest.update(ref, true);
	}

	@PreAuthorize( "@auth.hasRole('USER') and@auth.canAddTags(@auth.tagPatch(#tags))")
	@Timed(value = "jasper.service", extraTags = {"service", "tag"}, histogram = true)
	public void respond(List<String> tags, String url) {
		var ref = getResponseRef(url);
		for (var tag : tags) {
			if (tag.startsWith("-")) {
				removeTag(ref.getTags(), tag.substring(1));
			} else if (!ref.getTags().contains(tag)) {
				ref.getTags().add(tag);
			}
		}
		ingest.update(ref, true);
	}

	private Ref getResponseRef(String url) {
		var userUrl = urlForUser(url, auth.getUserTag().tag);
		return refRepository.findOneByUrlAndOrigin(userUrl, auth.getOrigin()).map(ref -> {
				if (!ref.getSources().contains(url)) ref.setSources(new ArrayList<>(List.of(url)));
				if (ref.getTags().contains("plugin/deleted")) {
					ref.setTags(new ArrayList<>(List.of("internal", auth.getUserTag().tag)));
				}
				return ref;
			})
			.orElseGet(() -> {
				var ref = new Ref();
				ref.setUrl(userUrl);
				ref.setOrigin(auth.getOrigin());
				ref.setSources(new ArrayList<>(List.of(url)));
				ref.setTags(new ArrayList<>(List.of("internal", auth.getUserTag().tag)));
				ingest.ingest(ref, false);
				return ref;
			});
	}
}
