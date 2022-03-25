package ca.hc.jasper.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.*;
import javax.validation.constraints.NotNull;

import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;

@Entity
@Getter
@Setter
@IdClass(TagId.class)
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class Tag {

	@Id
	@NotNull
	private String tag;

	@Id
	private String origin = "";

	private String name;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> pinned;

	private int color;

	private int textColor;

	private Instant modified = Instant.now();

	public TagId getId() {
		return new TagId(tag, origin);
	}

	public boolean local() {
		return origin == null || origin.isBlank();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Tag tag1 = (Tag) o;
		return tag.equals(tag1.tag) && origin.equals(tag1.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag, origin);
	}
}
