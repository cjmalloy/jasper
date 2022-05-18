package jasper.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jasper.domain.proj.IsTag;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class UserDto implements IsTag {
	private String tag;
	private String origin;
	private String name;
	private List<String> readAccess;
	private List<String> writeAccess;
	private List<String> tagReadAccess;
	private List<String> tagWriteAccess;
	private Instant modified;
	private byte[] pubKey;
}
