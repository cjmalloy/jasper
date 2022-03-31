package ca.hc.jasper.domain;

import java.time.Instant;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import ca.hc.jasper.domain.proj.IsTag;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
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
public class Tag implements IsTag {
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

	@Formula("tag || origin")
	private String qualifiedTag;

	private String name;

	private int color;

	private int textColor;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private JsonNode config;

	@LastModifiedDate
	private Instant modified = Instant.now();

	@JsonIgnore
	public String getQualifiedTag() {
		return getTag() + getOrigin();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Tag tag = (Tag) o;
		return this.tag.equals(tag.tag) && origin.equals(tag.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag, origin);
	}
}
