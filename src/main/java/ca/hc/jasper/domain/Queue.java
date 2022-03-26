package ca.hc.jasper.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.*;
import javax.validation.constraints.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vladmihalcea.hibernate.type.interval.PostgreSQLIntervalType;
import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;
import org.hibernate.validator.constraints.URL;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@Setter
@IdClass(TagId.class)
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
@TypeDef(
	typeClass = PostgreSQLIntervalType.class,
	defaultForType = Duration.class
)
public class Queue {

	@Id
	@Column(updatable = false)
	@NotBlank
	@Pattern(regexp = TagId.REGEX)
	private String tag;

	@Id
	@Column(updatable = false)
	@URL
	private String origin = "";

	private String name;

	private String comment;

	private String bounty;

	@Column(columnDefinition = "interval")
	private Duration maxAge;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	@NotNull
	private List<String> approvers;

	@LastModifiedDate
	private Instant modified = Instant.now();

	@JsonIgnore
	public boolean local() {
		return origin == null || origin.isBlank();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Queue queue = (Queue) o;
		return tag.equals(queue.tag) && origin.equals(queue.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag, origin);
	}
}
