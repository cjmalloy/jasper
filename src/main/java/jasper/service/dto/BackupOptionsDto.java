package jasper.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BackupOptionsDto {
	private boolean ext;
	private boolean feed;
	private boolean origin;
	private boolean plugin;
	private boolean ref;
	private boolean template;
	private boolean user;
}
