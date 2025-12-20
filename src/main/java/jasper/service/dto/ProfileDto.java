package jasper.service.dto;

import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;

import java.io.Serializable;

import static jasper.domain.User.QTAG_REGEX;
import static jasper.domain.proj.Tag.QTAG_LEN;

public record ProfileDto(
	@Pattern(regexp = QTAG_REGEX)
	@Length(max = QTAG_LEN)
	String tag,
	boolean active,
	String password,
	String role
) implements Serializable {
}
