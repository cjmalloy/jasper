package jasper.component.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
public class ScimListResponse<T> {
	private List<String> schemas;
	private long totalResults;
	private Integer startIndex;
	private Integer itemsPerPage;
	@JsonProperty("Resources")
	private List<T> resources;
}
