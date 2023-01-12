package jasper.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.Tag;
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
import javax.persistence.Table;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@Setter
@IdClass(TagId.class)
@Table(name = "users")
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class User implements Tag {
	public static final String REGEX = "[_+]user/[a-z]+([./][a-z]+)*";
	public static final String QTAG_REGEX = REGEX + HasOrigin.REGEX;
	public static final int NAME_LEN = 512;
	public static final int PUB_KEY_LEN = 4096;

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
	private String qualifiedTag;

	@Length(max = NAME_LEN)
	private String name;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<@Length(max = TAG_LEN) @Pattern(regexp = Tag.REGEX) String> readAccess;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<@Length(max = TAG_LEN) @Pattern(regexp = Tag.REGEX) String> writeAccess;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<@Length(max = TAG_LEN) @Pattern(regexp = Tag.REGEX) String> tagReadAccess;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<@Length(max = TAG_LEN) @Pattern(regexp = Tag.REGEX) String> tagWriteAccess;

	@Id
	@Column(updatable = false)
	@LastModifiedDate
	private Instant modified = Instant.now();

	@Size(max = PUB_KEY_LEN)
	private byte[] pubKey;

	@JsonIgnore
	public String getQualifiedTag() {
		return getTag() + getOrigin();
	}

	@JsonIgnore
	public User addReadAccess(List<String> toAdd) {
		if (toAdd == null) return this;
		if (readAccess == null) {
			readAccess = toAdd;
		} else {
			for (var t : toAdd) {
				if (!readAccess.contains(t)) {
					readAccess.add(t);
				}
			}
		}
		return this;
	}

	@JsonIgnore
	public User addWriteAccess(List<String> toAdd) {
		if (toAdd == null) return this;
		if (writeAccess == null) {
			writeAccess = toAdd;
		} else {
			for (var t : toAdd) {
				if (!writeAccess.contains(t)) {
					writeAccess.add(t);
				}
			}
		}
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		User user = (User) o;
		return tag.equals(user.tag) && origin.equals(user.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag, origin);
	}
}
