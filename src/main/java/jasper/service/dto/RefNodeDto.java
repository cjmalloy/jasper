package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.proj.HasTags;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@JsonInclude(NON_EMPTY)
public record RefNodeDto(
	String url,
	String origin,
	String title,
	String comment,
	List<String> tags,
	List<String> sources,
	List<String> responses,
	List<String> alternateUrls,
	ObjectNode plugins,
	MetadataDto metadata,
	Instant published,
	Instant created,
	Instant modified
) implements HasTags, Serializable {

// Helper methods for MapStruct
public RefNodeDto withTags(List<String> tags) {
return new RefNodeDto(url, origin, title, comment, tags, sources, responses, alternateUrls, plugins, metadata, published, created, modified);
}

public RefNodeDto withPlugins(ObjectNode plugins) {
return new RefNodeDto(url, origin, title, comment, tags, sources, responses, alternateUrls, plugins, metadata, published, created, modified);
}
}
