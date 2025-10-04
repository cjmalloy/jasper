package jasper.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagId implements Serializable {

	private String tag;
	private String origin;
	private Instant modified;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TagId tagId = (TagId) o;
		return tag.equals(tagId.tag) && origin.equals(tagId.origin) && modified.equals(tagId.modified);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag, origin, modified);
	}
}
