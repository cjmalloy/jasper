package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jasper.domain.proj.Tag;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserDto implements Tag, Serializable {
	private String tag;
	private String origin;
	private String name;
	private String role;
	private List<String> readAccess;
	private List<String> writeAccess;
	private List<String> tagReadAccess;
	private List<String> tagWriteAccess;
	private Instant modified;
	private byte[] pubKey;
	private String authorizedKeys;
	private ExternalDto external;

	public boolean hasExternalId() {
		if (external == null) return false;
		if (external.getIds() == null) return false;
		return !external.getIds().isEmpty();
	}

	public boolean hasExternalId(String id) {
		if (external == null) return false;
		if (external.getIds() == null) return false;
		return external.getIds().contains(id);
	}
}
