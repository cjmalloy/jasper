package ca.hc.jasper.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.*;
import javax.validation.constraints.*;

import ca.hc.jasper.domain.proj.HasTags;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@Setter
@IdClass(RefId.class)
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class Ref implements HasTags {

	@Id
	@Column(updatable = false)
	@NotBlank
	private String url;

	@Id
	@Column(updatable = false)
	@Pattern(regexp = Origin.REGEX)
	private String origin = "";

	private String title;

	private String comment;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<@Pattern(regexp = TagId.REGEX) String> tags;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> sources;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> alternateUrls;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private ObjectNode plugins;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private Metadata metadata;

	@Column(updatable = false)
	@NotNull
	private Instant published = Instant.now();

	@CreatedDate
	@Column(updatable = false)
	private Instant created = Instant.now();

	@LastModifiedDate
	private Instant modified = Instant.now();

	@JsonIgnore
	public Ref addTags(List<String> toAdd) {
		if (toAdd == null) return this;
		if (tags == null) {
			tags = toAdd;
		} else {
			for (var t : toAdd) {
				if (!tags.contains(t)) {
					tags.add(t);
				}
			}
		}
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Ref ref = (Ref) o;
		return url.equals(ref.url) && origin.equals(ref.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(url, origin);
	}
}
