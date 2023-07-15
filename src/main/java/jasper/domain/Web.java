package jasper.domain;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;
import org.springframework.data.annotation.CreatedDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.time.Instant;
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

	private String mime;

	private byte[] data;

	@CreatedDate
	@Column(updatable = false)
	private Instant scraped = Instant.now();

	public static Web from(byte[] data, String mime) {
		var result = new Web();
		result.url = "internal:" + UUID.randomUUID();
		result.mime = mime;
		result.data = data;
		return result;
	}
}
