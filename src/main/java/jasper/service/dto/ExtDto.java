package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.proj.Tag;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExtDto implements Tag, Serializable {
	private String tag;
	private String origin;
	private String name;
	private ObjectNode config;
	private Instant modified;
}
