package jasper.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

import jasper.domain.proj.IsTag;
import jasper.repository.spec.QualifiedTag;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@Setter
@IdClass(TagId.class)
@Table(name = "users")
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class User implements IsTag {
	public static final String REGEX = "[_+]user/[a-z]+(/[a-z]+)*";

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

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<@Pattern(regexp = QualifiedTag.REGEX) String> readAccess;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<@Pattern(regexp = QualifiedTag.REGEX) String> writeAccess;

	@LastModifiedDate
	private Instant modified = Instant.now();

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
