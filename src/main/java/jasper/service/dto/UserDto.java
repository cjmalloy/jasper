package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jasper.domain.proj.Tag;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@JsonInclude(NON_EMPTY)
public record UserDto(
	String tag,
	String origin,
	String name,
	String role,
	List<String> readAccess,
	List<String> writeAccess,
	List<String> tagReadAccess,
	List<String> tagWriteAccess,
	Instant modified,
	byte[] pubKey,
	String authorizedKeys,
	ExternalDto external
) implements Tag, Serializable {
	
	// Helper methods for MapStruct
	public UserDto withReadAccess(List<String> readAccess) {
		return new UserDto(tag, origin, name, role, readAccess, writeAccess, tagReadAccess, tagWriteAccess, modified, pubKey, authorizedKeys, external);
	}
	
	public UserDto withWriteAccess(List<String> writeAccess) {
		return new UserDto(tag, origin, name, role, readAccess, writeAccess, tagReadAccess, tagWriteAccess, modified, pubKey, authorizedKeys, external);
	}
}
