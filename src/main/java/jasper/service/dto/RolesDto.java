package jasper.service.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RolesDto {
	boolean debug;
	String tag;
	boolean sysadmin;
	boolean admin;
	boolean mod;
	boolean editor;
	boolean user;
	boolean viewer;
}
