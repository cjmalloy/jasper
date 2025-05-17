package jasper.component;

import io.micrometer.core.annotation.Timed;
import jasper.domain.Metadata;
import jasper.domain.Ref;
import jasper.domain.Ref_;
import jasper.repository.RefRepository;
import jasper.repository.spec.OriginSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static jasper.domain.proj.Tag.matchesTemplate;
import static jasper.repository.spec.OriginSpec.isUnderOrigin;
import static jasper.repository.spec.RefSpec.hasInternalResponse;
import static jasper.repository.spec.RefSpec.hasResponse;
import static jasper.repository.spec.RefSpec.hasSource;
import static jasper.repository.spec.RefSpec.hasTag;
import static jasper.repository.spec.RefSpec.isUrl;
import static jasper.repository.spec.RefSpec.isUrls;
import static java.time.Instant.now;
import static org.springframework.data.domain.Sort.Order.desc;
import static org.springframework.data.domain.Sort.by;

@Component
public class Meta {
	private static final Logger logger = LoggerFactory.getLogger(Meta.class);

	@Autowired
	RefRepository refRepository;

	@Autowired
	Messages messages;

	private record PluginResponses(String tag, long count) { }
	private record UserUrlResponse(String tag, List<String> responses) { }

	@Timed(value = "jasper.meta", histogram = true)
	public void ref(String rootOrigin, Ref ref) {
		if (ref == null) return;
		ref.setMetadata(Metadata
			.builder()
			.responses(refRepository.findAllResponsesWithoutTag(ref.getUrl(), rootOrigin, "internal"))
			.internalResponses(refRepository.findAllResponsesWithTag(ref.getUrl(), rootOrigin, "internal"))
			.userUrls(refRepository.findAllUserPluginTagsInResponses(ref.getUrl(), rootOrigin)
				.stream()
				.map(tag -> new UserUrlResponse(
					tag,
					refRepository.findAllResponsesWithTag(ref.getUrl(), rootOrigin, tag)))
				.filter(p -> !p.responses.isEmpty())
				.collect(Collectors.toMap(UserUrlResponse::tag, UserUrlResponse::responses)))
			.plugins(refRepository.findAllPluginTagsInResponses(ref.getUrl(), rootOrigin)
				.stream()
				.map(tag -> new PluginResponses(
					tag,
					refRepository.count(hasSource(ref.getUrl()).and(isUnderOrigin(rootOrigin)).and(hasTag(tag)))))
				.filter(p -> p.count() > 0)
				.collect(Collectors.toMap(PluginResponses::tag, PluginResponses::count)))
			.build()
		);

		// Set Not Obsolete
		ref.getMetadata().setObsolete(false);
	}

	@Timed(value = "jasper.meta", histogram = true)
	public void regen(String rootOrigin, Ref ref) {
		var originalDate = ref.getMetadata() == null ? now().toString() : ref.getMetadata().getModified();
		ref(rootOrigin, ref);
		ref.getMetadata().setModified(originalDate);
		ref.getMetadata().setObsolete(refRepository.newerExists(ref.getUrl(), rootOrigin, ref.getModified()));
		if (ref.getMetadata().isObsolete()) return;
		refRepository.setObsolete(ref.getUrl(), ref.getOrigin(), rootOrigin, ref.getModified());
		var cleanupSources = refRepository.findAll(OriginSpec.<Ref>isUnderOrigin(rootOrigin)
			.and(hasResponse(ref.getUrl()).or(hasInternalResponse(ref.getUrl()))));
		for (var source : cleanupSources) {
			if (ref.getSources() != null && ref.getSources().contains(source.getUrl())) {
				ref(rootOrigin, source);
			} else {
				removeSource(rootOrigin, source, ref);
			}
		}
	}

	@Timed(value = "jasper.meta", histogram = true)
	public void sources(String rootOrigin, Ref ref, Ref existing) {
		if (ref != null) {
			// Creating or updating (not deleting)
			refRepository.setObsolete(ref.getUrl(), ref.getOrigin(), rootOrigin, ref.getModified());

			// Update sources
			List<Ref> sources = refRepository.findAll(isUrls(ref.getSources()).and(isUnderOrigin(rootOrigin)));
			for (var source : sources) {
				if (source.getUrl().equals(ref.getUrl())) continue;
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
				if (existing != null && existing.getTags() != null) {
					metadata.removePlugins(existing.getTags().stream()
							.filter(tag -> matchesTemplate("plugin", tag))
							.toList(),
						ref.getUrl());
				}
				metadata.addPlugins(ref.getTags().stream()
					.filter(tag -> matchesTemplate("plugin", tag))
					.toList(),
					ref.getUrl());
				source.setMetadata(metadata);
				try {
					refRepository.save(source);
					messages.updateMetadata(source);
				} catch (DataAccessException e) {
					logger.error("Error updating source metadata for {} {}", ref.getOrigin(), ref.getUrl(), e);
				}
			}
		} else {
			// Deleting
			var maybeLatest = refRepository.findAll(isUrl(existing.getUrl()).and(isUnderOrigin(rootOrigin)), PageRequest.of(0, 1, by(desc(Ref_.MODIFIED))));
			if (!maybeLatest.isEmpty()) {
				var latest = maybeLatest.getContent().get(0);
				if (latest.getMetadata() != null) {
					latest.getMetadata().setObsolete(false);
					refRepository.save(latest);
					messages.updateMetadata(latest);
				}
			}
		}

		if (existing != null && existing.getSources() != null) {
			// Updating or deleting (not new)
			var removedSources = ref == null
				? existing.getSources()
				: existing.getSources().stream()
					.filter(s -> ref.getSources() == null || !ref.getSources().contains(s))
					.toList();
			List<Ref> removed = refRepository.findAll(isUrls(removedSources).and(isUnderOrigin(rootOrigin)));
			for (var source : removed) {
				if (source.getUrl().equals(existing.getUrl())) continue;
				removeSource(rootOrigin, source, existing);
				messages.updateMetadata(source);
			}
		}
	}

	private void removeSource(String rootOrigin, Ref source, Ref existing) {
		var metadata = source.getMetadata();
		if (metadata == null) return;
		metadata.remove(existing.getUrl());
		if (existing.getTags() != null) {
			metadata.removePlugins(existing.getTags().stream()
					.filter(tag -> matchesTemplate("plugin", tag))
					.toList(),
				existing.getUrl());
		}
		source.setMetadata(metadata);
		try {
			refRepository.save(source);
		} catch (DataAccessException e) {
			logger.error("{} Error updating source metadata for {} {}",
				rootOrigin, source.getOrigin(), source.getUrl(), e);
		}
	}
}
