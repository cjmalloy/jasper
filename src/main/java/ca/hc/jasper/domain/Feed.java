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
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class Feed {

	@Id
	private String origin;

	private String name;

	private String proxy;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> tags;

	private Instant modified;

	private Instant lastScrape;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Feed feed = (Feed) o;
		return origin.equals(feed.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(origin);
	}
}
