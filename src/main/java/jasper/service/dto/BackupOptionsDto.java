package jasper.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BackupOptionsDto {
	public static final String ID_INVALID_REGEX = "[./\\]";
	public static final int ID_LEN = 256;
	private boolean ref;
	private boolean ext;
	private boolean user;
	private boolean plugin;
	private boolean template;
	private Instant newerThan;
}
