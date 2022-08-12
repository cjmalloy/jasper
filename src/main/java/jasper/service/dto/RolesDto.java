package jasper.service.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RolesDto {
	String tag;
	boolean admin;
	boolean mod;
	boolean editor;
}
