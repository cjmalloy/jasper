package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jasper.domain.proj.Tag;
import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

@Builder(toBuilder = true)
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
}
