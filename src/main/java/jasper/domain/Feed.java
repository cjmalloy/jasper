package jasper.domain;

import com.vladmihalcea.hibernate.type.interval.PostgreSQLIntervalType;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jasper.domain.proj.HasTags;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static jasper.domain.Origin.ORIGIN_LEN;
import static jasper.domain.Ref.URL_LEN;
import static jasper.domain.TagId.TAG_LEN;

@Entity
@Getter
@Setter
@IdClass(RefId.class)
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class),
	@TypeDef(typeClass = PostgreSQLIntervalType.class, defaultForType = Duration.class)
})
public class Feed implements HasTags {
	public static final int NAME_LEN = 512;

	@Id
	@Column(updatable = false)
	@NotBlank
	@URL
	@Length(max = URL_LEN)
	private String url;

	@Id
	@Column(updatable = false)
	@Pattern(regexp = Origin.REGEX)
	@Length(max = ORIGIN_LEN)
	private String origin = "";

	@Length(max = NAME_LEN)
	private String name;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<@Length(max = TAG_LEN) @Pattern(regexp = TagId.REGEX) String> tags;

	@LastModifiedDate
	private Instant modified = Instant.now();

	private Instant lastScrape;

	@Column(columnDefinition = "interval")
	private Duration scrapeInterval;

	private boolean scrapeDescription = true;

	private boolean removeDescriptionIndent = false;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Feed feed = (Feed) o;
		return url.equals(feed.url) && origin.equals(feed.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(url, origin);
	}
}
