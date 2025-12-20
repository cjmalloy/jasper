package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.proj.Tag;
import java.io.Serializable;
import java.time.Instant;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@JsonInclude(NON_EMPTY)
public record TemplateDto(
	String tag,
	String origin,
	String name,
	ObjectNode config,
	ObjectNode defaults,
	@JsonInclude() ObjectNode schema,
	Instant modified
) implements Tag, Serializable {
}
