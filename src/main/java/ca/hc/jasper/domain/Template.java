package ca.hc.jasper.domain;

import java.time.Instant;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.proj.IsTag;
import ca.hc.jasper.domain.validator.SchemaValid;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@Setter
@IdClass(TagId.class)
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class Template implements IsTag {
	public static final String REGEX = "_?[a-z]+(/[a-z]+)*";

	@Id
	@Column(updatable = false)
	@NotBlank
	@Pattern(regexp = REGEX)
	private String tag;

	@Id
	@Column(updatable = false)
	@Pattern(regexp = Origin.REGEX)
	private String origin = "";

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private JsonNode config;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private JsonNode defaults;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	@SchemaValid
	private ObjectNode schema;

	@LastModifiedDate
	private Instant modified = Instant.now();

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Template template = (Template) o;
		return tag.equals(template.tag) && origin.equals(template.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag, origin);
	}
}