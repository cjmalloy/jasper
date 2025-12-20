package jasper.service.dto;

import lombok.Builder;

import java.io.Serializable;

@Builder
public record BackupDto(
	String id,
	long size
) implements Serializable {
}
