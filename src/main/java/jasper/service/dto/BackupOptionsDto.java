package jasper.service.dto;

import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;

@Builder
public record BackupOptionsDto(
	boolean cache,
	boolean ref,
	boolean ext,
	boolean user,
	boolean plugin,
	boolean template,
	Instant newerThan
) implements Serializable {
	public static final int ID_LEN = 256;
}
