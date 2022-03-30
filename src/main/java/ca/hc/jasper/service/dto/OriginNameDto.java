package ca.hc.jasper.service.dto;

import java.time.Instant;

import ca.hc.jasper.domain.proj.HasOrigin;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OriginNameDto implements HasOrigin {
	private String origin;
	private String name;
	private Instant modified;
}
