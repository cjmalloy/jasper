package ca.hc.jasper.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.*;
import javax.validation.constraints.*;

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
@IdClass(TagId.class)
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class User {

	@Id
	@NotBlank
	@Pattern(regexp = TagId.REGEX)
	private String tag;

	@Id
	@URL
	private String origin = "";

	private String name;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> watches;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> subscriptions;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> readAccess;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> writeAccess;

	private Instant lastLogin;

	@LastModifiedDate
	private Instant modified = Instant.now();

	private byte[] pubkey;

	@JsonIgnore
	public TagId getId() {
		return new TagId(tag, origin);
	}

	@JsonIgnore
	public boolean local() {
		return origin == null || origin.isBlank();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		User user = (User) o;
		return tag.equals(user.tag) && origin.equals(user.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag, origin);
	}
}
