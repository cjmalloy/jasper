package ca.hc.jasper.service.dto;

import lombok.*;

@Getter
@Builder
public class RolesDto {
	String tag;
	boolean admin;
	boolean mod;
	boolean editor;
}
