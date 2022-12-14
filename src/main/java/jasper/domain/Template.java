package jasper.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.validation.constraints.Pattern;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.Tag;
import jasper.domain.validator.SchemaValid;
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

import static jasper.config.JacksonConfiguration.om;

@Entity
@Getter
@Setter
@IdClass(TagId.class)
public class Template implements Tag {
	public static final String REGEX = "(?:_?[a-z0-9]+(?:[./][a-z0-9]+)*)?";
	public static final String QTAG_REGEX = REGEX + HasOrigin.REGEX;
	public static final int NAME_LEN = 512;

	@Id
	@Column(updatable = false)
	@Pattern(regexp = REGEX)
	@Length(max = TAG_LEN)
	private String tag = "";

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

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private JsonNode defaults;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	@SchemaValid
	private ObjectNode schema;

	@LastModifiedDate
	private Instant modified = Instant.now();

	@Formula("CASE WHEN tag = '' THEN 0 ELSE ARRAY_LENGTH(regexp_split_to_array(tag, '/'), 1) END")
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
		Template template = (Template) o;
		return tag.equals(template.tag) && origin.equals(template.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag, origin);
	}

	@JsonIgnore
	public <T> T getConfig(Class<T> toValueType) {
		if (config == null) return null;
		return om().convertValue(config, toValueType);
	}
}
