package jasper.service.dto;

import java.io.Serializable;

public record BackupDto(
	String id,
	long size
) implements Serializable {
}
