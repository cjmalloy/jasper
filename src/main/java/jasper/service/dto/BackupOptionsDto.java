package jasper.service.dto;

import java.io.Serializable;
import java.time.Instant;

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
	
	// Convenience method to get newerThan (for backward compatibility with old getter)
	public Instant getNewerThan() {
		return newerThan;
	}
}
