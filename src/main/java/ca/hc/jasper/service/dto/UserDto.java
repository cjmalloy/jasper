package ca.hc.jasper.service.dto;

import java.time.Instant;
import java.util.List;

import ca.hc.jasper.domain.proj.IsTag;
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
	private Instant modified;
	private byte[] pubKey;
}
