package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.proj.Tag;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TemplateDto implements Tag {
	private String tag;
	private String origin;
	private String name;
	private JsonNode config;
	private JsonNode defaults;
	private ObjectNode schema;
	private Instant modified;
}
