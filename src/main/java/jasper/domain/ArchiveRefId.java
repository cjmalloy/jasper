package jasper.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Composite key for {@link Ref} when the "archive" profile is active.
 * See config/archive-orm.xml.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveRefId implements Serializable {
	private String url;
	private String origin;
	private Instant modified;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ArchiveRefId refId = (ArchiveRefId) o;
		return Objects.equals(url, refId.url) && Objects.equals(origin, refId.origin) && Objects.equals(modified, refId.modified);
	}

	@Override
	public int hashCode() {
		return Objects.hash(url, origin, modified);
	}
}
