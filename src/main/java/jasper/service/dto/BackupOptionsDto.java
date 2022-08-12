package jasper.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BackupOptionsDto {
	private boolean ref;
	private boolean ext;
	private boolean user;
	private boolean plugin;
	private boolean template;
}
