package jasper.component;

import io.micrometer.core.annotation.Timed;
import jakarta.persistence.EntityManager;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.repository.RefRepository;
import jasper.repository.RefRepositoryCustom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static jasper.domain.proj.Tag.matchesTemplate;
import static jasper.repository.spec.OriginSpec.isUnderOrigin;
import static jasper.repository.spec.RefSpec.isNotObsolete;
import static jasper.repository.spec.RefSpec.isUrl;
import static jasper.repository.spec.RefSpec.isUrls;
import static java.time.Instant.now;
import static java.util.stream.Collectors.toMap;
import static org.springframework.data.domain.Sort.Order.desc;
import static org.springframework.data.domain.Sort.by;

@Component
public class Meta {
	private static final Logger logger = LoggerFactory.getLogger(Meta.class);

	/**
	 * Number of sources already updated synchronously on cascade-queued Refs.
	 */
	public static final int SYNC_SOURCES = 2;

	@Autowired
	RefRepository refRepository;

	@Autowired
	RefRepositoryCustom refRepositoryCustom;

	@Autowired
	Messages messages;

	@Autowired
	EntityManager em;

	private record UserUrlResponse(String tag, List<String> responses) { }

	@Timed(value = "jasper.meta", histogram = true)
	public void ref(String rootOrigin, Ref ref) {
		if (ref == null) return;
		ref.setMetadata(Metadata
			.builder()
			.expandedTags(expandTags(ref.getTags()))
			.responses(refRepository.findAllResponsesWithoutTag(ref.getUrl(), rootOrigin, "internal"))
			.internalResponses(refRepository.findAllResponsesWithTag(ref.getUrl(), rootOrigin, "internal"))
			.userUrls(refRepositoryCustom.findAllUserPluginTagsInResponses(ref.getUrl(), rootOrigin)
				.stream()
				.map(tag -> new UserUrlResponse(
					tag,
					refRepository.findAllResponsesWithTag(ref.getUrl(), rootOrigin, tag)))
				.filter(p -> !p.responses.isEmpty())
				.collect(toMap(UserUrlResponse::tag, UserUrlResponse::responses)))
			.plugins(refRepositoryCustom.countPluginTagsInResponses(ref.getUrl(), rootOrigin)
				.stream()
				.collect(toMap(r -> (String) r[0], r -> ((Number) r[1]).longValue())))
			.build()
		);
	}

	@Timed(value = "jasper.meta", histogram = true)
	public void update(String rootOrigin, Ref ref, Ref existing) {
		if (existing == null || existing.getMetadata() == null) {
			ref(rootOrigin, ref);
			return;
		}
		if (ref == null) return;
		ref.setMetadata(existing.getMetadata().toBuilder()
			.expandedTags(expandTags(ref.getTags()))
			.obsolete(false)
			.build()
		);
	}

	@Timed(value = "jasper.meta", histogram = true)
	public void response(String rootOrigin, Ref ref) {
		if (ref == null) return;
		ref.setMetadata(Metadata
			.builder()
			.expandedTags(expandTags(ref.getTags()))
			.build()
		);
	}

	@Timed(value = "jasper.meta", histogram = true)
	public void responseSource(String rootOrigin, Ref ref, Ref existing) {
		if (ref != null && existing != null && existing.getTags() != null && existing.getTags().equals(ref.getTags())) return;
		sources(rootOrigin, ref, existing);
	}

	public static List<String> expandTags(List<String> tags) {
		if (tags == null) return new ArrayList<>();
		var result = new ArrayList<>(tags);
		for (var i = result.size() - 1; i >= 0; i--) {
			var t = result.get(i);
			while (t.contains("/")) {
				t = t.substring(0, t.lastIndexOf("/"));
				if (!result.contains(t)) {
					result.add(t);
				}
			}
		}
		return result;
	}

	@Timed(value = "jasper.meta", histogram = true)
	public void regen(String rootOrigin, Ref ref) {
		var originalDate = ref.getMetadata() == null ? now().toString() : ref.getMetadata().getModified();
		ref(rootOrigin, ref);
		ref.getMetadata().setModified(originalDate);
		ref.getMetadata().setObsolete(refRepository.newerExists(ref.getUrl(), rootOrigin, ref.getModified()));
		if (ref.getMetadata().isObsolete()) return;
		refRepository.updateObsolete(ref.getUrl(), rootOrigin);
		var sources = (ref.getSources() == null ? List.<String>of() : ref.getSources())
			.stream()
			.limit(SYNC_SOURCES)
			.filter(s -> !s.equals(ref.getUrl()))
			.distinct()
			.toList();
		if (!sources.isEmpty()) for (var source : refRepository.findAll(isUrls(sources).and(isNotObsolete()).and(isUnderOrigin(rootOrigin)))) {
			cascadeSource(rootOrigin, ref, source);
		}
		ref.getMetadata().setCascade(true);
	}

	public void cascadeSource(String rootOrigin, Ref ref, Ref source) {
		detach(source);
		var originalDate = source.getMetadata() == null ? now().toString() : source.getMetadata().getModified();
		var regen = source.getMetadata() != null && source.getMetadata().isRegen();
		var cascade = source.getMetadata() != null && source.getMetadata().isCascade();
		ref(rootOrigin, source);
		source.getMetadata().setModified(originalDate);
		source.getMetadata().setRegen(regen);
		source.getMetadata().setCascade(cascade);
		try {
			refRepository.updateMetadata(source.getUrl(), source.getOrigin(), source.getMetadata());
			messages.updateMetadata(source);
		} catch (DataAccessException e) {
			logger.error("Error updating source metadata for {} {}", ref.getOrigin(), ref.getUrl(), e);
		}
	}

	@Timed(value = "jasper.meta", histogram = true)
	public void sources(String rootOrigin, Ref ref, Ref existing) {
		if (ref == null) {
			// Deleting
			var maybeLatest = refRepository.findAll(isUrl(existing.getUrl()).and(isUnderOrigin(rootOrigin)), PageRequest.of(0, 1, by(desc(Ref_.MODIFIED))));
			if (!maybeLatest.isEmpty()) {
				// Deleting make a shadowed Ref visible
				var latest = maybeLatest.getContent().getFirst();
				detach(latest);
				if (latest.getMetadata() != null && existing.getMetadata() != null) {
					latest.getMetadata().setModified(existing.getMetadata().getModified());
				}
				regen(rootOrigin, latest);
				refRepository.updateMetadata(latest.getUrl(), latest.getOrigin(), latest.getMetadata());
				messages.updateMetadata(latest);
			} else {
				try (var stream = refRepository.findRemovedSources(existing.getUrl(), rootOrigin)) {
					stream.forEach(source -> removeSource(rootOrigin, existing.getUrl(), source, existing));
				}
			}
			return;
		}

		// Creating or updating (not deleting)
		var cascade = false;
		refRepository.updateObsolete(ref.getUrl(), rootOrigin);
		if (ref.getSources() != null && ref.getSources().size() > SYNC_SOURCES) {
			cascade = true;
		}

		// Update sources
		var sources = (ref.getSources() == null ? List.<String>of() : ref.getSources())
			.stream()
			.limit(SYNC_SOURCES)
			.filter(s -> !s.equals(ref.getUrl()))
			.distinct()
			.toList();
		if (!sources.isEmpty()) for (var source : refRepository.findAll(isUrls(sources).and(isNotObsolete()).and(isUnderOrigin(rootOrigin)))) {
			detach(source);
			var metadata = source.getMetadata();
			if (metadata == null) {
				logger.debug("Ref missing metadata: {}", ref.getUrl());
				metadata = Metadata
					.builder()
					.responses(new ArrayList<>())
					.internalResponses(new ArrayList<>())
					.plugins(new HashMap<>())
					.build();
			}
			if (ref.hasTag("internal")) {
				metadata.addInternalResponse(ref.getUrl());
			} else {
				metadata.addResponse(ref.getUrl());
			}
			if (existing != null) {
				metadata.removePlugins(existing.getExpandedTags().stream()
						.filter(tag -> matchesTemplate("plugin", tag))
						.toList(),
					ref.getUrl());
			}
			metadata.addPlugins(ref.getExpandedTags().stream()
				.filter(tag -> matchesTemplate("plugin", tag))
				.toList(),
				ref.getUrl());
			source.setMetadata(metadata);
			try {
				refRepository.updateMetadata(source.getUrl(), source.getOrigin(), metadata);
				messages.updateMetadata(source);
			} catch (DataAccessException e) {
logger.error("{} Error updating source metadata for ({}) {}", rootOrigin, ref.getOrigin(), ref.getUrl(), e);
			}
		}

		if (existing != null && existing.getSources() != null) {
			// Updating
			var syncRemoved = existing.getSources()
					.stream()
					.limit(SYNC_SOURCES)
					.filter(s -> !s.equals(existing.getUrl()) && (ref.getSources() == null || !ref.getSources().contains(s)))
					.toList();
			var removed = refRepository.findAll(isUrls(syncRemoved).and(isNotObsolete()).and(isUnderOrigin(rootOrigin)));
			for (var source : removed) {
				removeSource(rootOrigin, existing.getUrl(), source, existing);
			}
			if (!cascade) {
				var removedSources = existing.getSources()
					.stream()
					.filter(s -> !s.equals(existing.getUrl()) && (ref.getSources() == null || !ref.getSources().contains(s)))
					.count();
				if (removedSources > syncRemoved.size()) {
					cascade = true;
				}
			}
		}
		if (cascade) {
			ref.getMetadata().setCascade(true);
			refRepository.markCascade(ref.getUrl(), ref.getOrigin());
		}
	}

	private void removeSource(String rootOrigin, String url, Ref source, Ref existing) {
		var metadata = source.getMetadata();
		if (metadata == null) return;
		detach(source);
		metadata.remove(url);
		if (existing != null) {
			metadata.removePlugins(existing.getExpandedTags().stream()
					.filter(tag -> matchesTemplate("plugin", tag))
					.toList(),
				url);
		}
		source.setMetadata(metadata);
		try {
			refRepository.updateMetadata(source.getUrl(), source.getOrigin(), metadata);
			messages.updateMetadata(source);
		} catch (DataAccessException e) {
			logger.error("{} Error updating source metadata for {} {}",
				rootOrigin, source.getOrigin(), source.getUrl(), e);
		}
	}

	/**
	 * Only metadata is written, so make sure a stale copy of the content is never flushed.
	 */
	private void detach(Ref ref) {
		if (em.contains(ref)) em.detach(ref);
	}
}
