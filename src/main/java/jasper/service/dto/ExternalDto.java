package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.io.Serializable;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@Builder
@JsonInclude(NON_EMPTY)
public record ExternalDto(
	List<String> ids
) implements Serializable {
}
