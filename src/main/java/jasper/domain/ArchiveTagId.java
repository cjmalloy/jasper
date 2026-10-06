package jasper.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Composite key for tag entities when the "archive" profile is active.
 * See config/archive-orm.xml.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveTagId implements Serializable {
	private String tag;
	private String origin;
	private Instant modified;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ArchiveTagId tagId = (ArchiveTagId) o;
		return Objects.equals(tag, tagId.tag) && Objects.equals(origin, tagId.origin) && Objects.equals(modified, tagId.modified);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag, origin, modified);
	}
}
