package ca.hc.jasper.domain;

import java.io.Serializable;
import java.util.Objects;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TemplateId implements Serializable {
	private String prefix;
	private String origin;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TemplateId templateId = (TemplateId) o;
		return prefix.equals(templateId.prefix) && origin.equals(templateId.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prefix, origin);
	}
}
