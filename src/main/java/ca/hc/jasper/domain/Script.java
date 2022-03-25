package ca.hc.jasper.domain;

import java.time.Instant;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Script {

	@Id
	@NotNull
	private String tag;

	private String name;

	@NotNull
	private String source;

	@NotNull
	private String language;

	private boolean error;

	private Instant modified = Instant.now();

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Script script = (Script) o;
		return tag.equals(script.tag);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag);
	}
}
