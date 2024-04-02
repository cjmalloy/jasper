package jasper.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.Tag;
import jasper.domain.validator.SchemaValid;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.hibernate.validator.constraints.Length;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Objects;

import static jasper.config.JacksonConfiguration.om;

@Entity
@Getter
@Setter
@IdClass(TagId.class)
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class Plugin implements Tag {
	public static final String REGEX = "[_+]?plugin(?:/[a-z0-9]+(?:[./][a-z0-9]+)*)?";
	public static final String QTAG_REGEX = REGEX + HasOrigin.REGEX;
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

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private ObjectNode config;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private JsonNode defaults;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	@SchemaValid
	private ObjectNode schema;

	private boolean generateMetadata = false;

	private boolean userUrl = false;

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
		Plugin plugin = (Plugin) o;
		return tag.equals(plugin.tag) && origin.equals(plugin.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag, origin);
	}

	public static boolean isPlugin(String tag) {
		return tag.startsWith("plugin/") ||
			tag.startsWith("_plugin/") ||
			tag.startsWith("+plugin/");
	}

	@JsonIgnore
	public <T> T getConfig(Class<T> toValueType) {
		if (config == null) return null;
		return om().convertValue(config, toValueType);
	}
}
