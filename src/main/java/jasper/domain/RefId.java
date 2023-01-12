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
public class RefId implements Serializable {
	private String url;
	private String origin;
	private Instant modified;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		RefId refId = (RefId) o;
		return url.equals(refId.url) && origin.equals(refId.origin) && modified.equals(refId.modified);
	}

	@Override
	public int hashCode() {
		return Objects.hash(url, origin, modified);
	}
}
