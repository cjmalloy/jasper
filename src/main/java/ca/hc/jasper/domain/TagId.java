package ca.hc.jasper.domain;

import java.io.Serializable;
import java.util.Objects;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagId implements Serializable {
	private String tag;
	private String origin;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TagId tagId = (TagId) o;
		return tag.equals(tagId.tag) && origin.equals(tagId.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag, origin);
	}
}
