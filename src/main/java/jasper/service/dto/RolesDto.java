package jasper.service.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RolesDto {
	String tag;
	boolean sysadmin;
	boolean admin;
	boolean mod;
	boolean editor;
	boolean user;
}
