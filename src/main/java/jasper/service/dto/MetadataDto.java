package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MetadataDto {
	private String modified = Instant.now().toString();
	private int responses;
	private int internalResponses;
	private Map<String, Integer> plugins;
	private List<String> userUrls;
	private boolean obsolete;
}
