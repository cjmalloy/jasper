package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.proj.HasTags;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@JsonInclude(NON_EMPTY)
public record RefReplDto(
	String url,
	String origin,
	String title,
	String comment,
	List<String> tags,
	List<String> sources,
	List<String> alternateUrls,
	ObjectNode plugins,
	Instant published,
	Instant created,
	Instant modified
) implements HasTags, Serializable {

// Helper methods for MapStruct
public RefReplDto withTags(List<String> tags) {
return new RefReplDto(url, origin, title, comment, tags, sources, alternateUrls, plugins, published, created, modified);
}

public RefReplDto withPlugins(ObjectNode plugins) {
return new RefReplDto(url, origin, title, comment, tags, sources, alternateUrls, plugins, published, created, modified);
}
}
