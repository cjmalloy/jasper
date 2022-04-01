package ca.hc.jasper.service.dto;

import java.time.Instant;

import ca.hc.jasper.domain.proj.HasOrigin;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class OriginNameDto implements HasOrigin {
	private String origin;
	private String name;
	private Instant modified;
}
