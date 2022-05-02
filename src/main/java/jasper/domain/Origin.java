package jasper.domain;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jasper.domain.proj.HasOrigin;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Objects;

import static jasper.domain.Ref.URL_LEN;

@Entity
@Getter
@Setter
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class Origin implements HasOrigin {
	public static final String REGEX_NOT_BLANK = "@[a-z]+(\\.[a-z])*";
	public static final String REGEX = "(" + REGEX_NOT_BLANK + ")?";
	public static final int ORIGIN_LEN = 64;
	public static final int NAME_LEN = 512;

	@Id
	@Column(updatable = false)
	@NotBlank
	@Pattern(regexp = REGEX_NOT_BLANK)
	@Length(max = ORIGIN_LEN)
	private String origin;

	@Column(updatable = false)
	@NotBlank
	@Length(max = URL_LEN)
	private String url;

	@Length(max = NAME_LEN)
	private String name;

	@URL
	@Length(max = URL_LEN)
	private String proxy;

	@LastModifiedDate
	private Instant modified = Instant.now();

	private Instant lastScrape;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Origin origin = (Origin) o;
		return this.origin.equals(origin.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(origin);
	}
}
