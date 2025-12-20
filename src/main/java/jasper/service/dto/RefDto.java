package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.proj.HasTags;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@JsonInclude(NON_EMPTY)
public record RefDto(
	String url,
	String origin,
	String title,
	String comment,
	List<String> tags,
	List<String> sources,
	List<String> alternateUrls,
	ObjectNode plugins,
	MetadataDto metadata,
	Instant published,
	Instant created,
	Instant modified
) implements HasTags, Serializable {
// Helper methods for MapStruct
public RefDto withTags(List<String> tags) {
return new RefDto(url, origin, title, comment, tags, sources, alternateUrls, plugins, metadata, published, created, modified);
}

public RefDto withPlugins(ObjectNode plugins) {
return new RefDto(url, origin, title, comment, tags, sources, alternateUrls, plugins, metadata, published, created, modified);
}
}
