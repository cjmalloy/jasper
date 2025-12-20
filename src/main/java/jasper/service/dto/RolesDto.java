package jasper.service.dto;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record RolesDto(
	boolean debug,
	String tag,
	boolean admin,
	boolean mod,
	boolean editor,
	boolean user,
	boolean viewer,
	boolean banned
) implements Serializable {
}
