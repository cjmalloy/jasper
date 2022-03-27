package ca.hc.jasper.service.dto;

import java.time.Instant;
import java.util.List;

import ca.hc.jasper.domain.proj.IsTag;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDto implements IsTag {
	private String tag;
	private String origin;
	private String name;
	private List<String> watches;
	private List<String> subscriptions;
	private List<String> readAccess;
	private List<String> writeAccess;
	private Instant lastLogin;
	private Instant modified;
	private byte[] pubKey;

	@JsonIgnore
	public boolean local() {
		return origin == null || origin.isBlank();
	}
}
