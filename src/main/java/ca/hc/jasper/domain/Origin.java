package ca.hc.jasper.domain;

import java.time.Instant;
import java.util.Objects;
import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.proj.IsTag;
import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.hibernate.validator.constraints.URL;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@Setter
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class Origin implements IsTag {
	public static final String REGEX = "_?origin/[a-z]+(/[a-z]+)*";
	public static final String REGEX_OR_BLANK = "(" + REGEX + ")?";

	@Id
	@Column(updatable = false)
	@NotBlank
	@Pattern(regexp = REGEX)
	private String tag;

	@Column(updatable = false)
	@NotBlank
	@URL
	private String url;

	private String name;

	@URL
	private String proxy;

	@LastModifiedDate
	private Instant modified = Instant.now();

	private Instant lastScrape;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Origin origin = (Origin) o;
		return tag.equals(origin.tag);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag);
	}
}
