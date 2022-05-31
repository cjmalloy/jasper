package jasper.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileDto {
	private String tag;
	private boolean active;
	private String password;
	private String role;
}
