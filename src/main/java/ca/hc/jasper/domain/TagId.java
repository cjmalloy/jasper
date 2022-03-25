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
		TagId refId = (TagId) o;
		return tag.equals(refId.tag) && Objects.equals(origin, refId.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag);
	}
}
