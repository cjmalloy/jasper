package jasper.service.dto;

import java.io.Serializable;

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
