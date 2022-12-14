package jasper.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.Tag;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.validator.constraints.Length;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@Setter
@IdClass(TagId.class)
public class Ext implements Tag {
	public static final int NAME_LEN = 512;

	@Id
	@Column(updatable = false)
	@NotBlank
	@Pattern(regexp = REGEX)
	@Length(max = TAG_LEN)
	private String tag;

	@Id
	@Column(updatable = false)
	@Pattern(regexp = HasOrigin.REGEX)
	@Length(max = ORIGIN_LEN)
	private String origin = "";

	@Formula("tag || origin")
	@Setter(AccessLevel.NONE)
	private String qualifiedTag;

	@Length(max = NAME_LEN)
	private String name;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private JsonNode config;

	@LastModifiedDate
	private Instant modified = Instant.now();

	@Formula("ARRAY_LENGTH(regexp_split_to_array(tag, '/'), 1)")
	@Setter(AccessLevel.NONE)
	private int levels;

	@JsonIgnore
	public String getQualifiedTag() {
		return getTag() + getOrigin();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Ext ext = (Ext) o;
		return tag.equals(ext.tag) && origin.equals(ext.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag, origin);
	}
}
