package ca.hc.jasper.service.dto;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDto {
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
}
