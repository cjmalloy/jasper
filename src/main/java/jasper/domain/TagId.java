package jasper.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

import static jasper.domain.Origin.ORIGIN_LEN;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagId implements Serializable {
	public static final String REGEX = "[_+]?[a-z]+(/[a-z]+)*";
	public static final String ADD_REMOVE_REGEX = "[-]?" + REGEX;
	public static final int TAG_LEN = 64;
	public static final int QTAG_LEN = TAG_LEN + ORIGIN_LEN + 1;

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
