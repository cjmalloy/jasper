package jasper.domain;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.util.UUID;


@Entity
@Getter
@Setter
public class Web {

	@Id
	@Column(updatable = false)
	@NotBlank
	@Pattern(regexp = Ref.REGEX)
	@Length(max = Ref.URL_LEN)
	private String url;

	private byte[] data;

	public static Web from(byte[] data) {
		var result = new Web();
		result.url = "internal:" + UUID.randomUUID();
		result.data = data;
		return result;
	}
}
