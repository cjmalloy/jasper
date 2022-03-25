package ca.hc.jasper.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.*;

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
	private String tag;

	@Id
	private String origin;

	private String name;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> pinned;

	private int color;

	private int textColor;

	private Instant modified;

	public TagId getId() {
		return new TagId(tag, origin);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Tag ref = (Tag) o;
		return tag.equals(ref.tag) && Objects.equals(origin, ref.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag);
	}
}
