package ca.hc.jasper.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.*;

import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;

@Entity
@Getter
@Setter
@IdClass(TagId.class)
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class User {

	@Id
	private String tag;

	@Id
	private String origin;

	private String name;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> watches;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> subscriptions;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> readAccess;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> writeAccess;

	private Instant lastLogin;

	private Instant modified;

	private byte[] pubkey;

	public TagId getId() {
		return new TagId(tag, origin);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		User ref = (User) o;
		return tag.equals(ref.tag) && Objects.equals(origin, ref.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tag);
	}
}
