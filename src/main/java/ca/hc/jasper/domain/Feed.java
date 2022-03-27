package ca.hc.jasper.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.proj.HasTags;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;
import org.hibernate.validator.constraints.URL;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@Setter
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class Feed implements HasTags {

	@Id
	@Column(updatable = false)
	@NotBlank
	@URL
	private String origin;

	private String name;

	@Column(updatable = false)
	@URL
	private String proxy;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<@Pattern(regexp = TagId.REGEX) String> tags;

	@LastModifiedDate
	private Instant modified = Instant.now();

	private Instant lastScrape;

	@JsonIgnore
	public boolean local() {
		return origin == null || origin.isBlank();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Feed feed = (Feed) o;
		return origin.equals(feed.origin) && name.equals(feed.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(origin, name);
	}
}
