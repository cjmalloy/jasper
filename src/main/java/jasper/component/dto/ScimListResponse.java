package jasper.component.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimListResponse<T> {
	private List<String> schemas;
	private long totalResults;
	private Integer startIndex;
	private Integer itemsPerPage;
	@JsonProperty("Resources")
	private List<T> resources;
}
