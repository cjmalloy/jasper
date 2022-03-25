package ca.hc.jasper.domain;

import java.time.Instant;
import java.util.*;
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
public class Invoice {

	@Id
	private UUID id;

	private String submitter;

	private String comment;

	private String queue;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<String> response;

	private String qr;

	private boolean paid;

	private boolean rejected;

	private boolean disputed;

	private Instant modified = Instant.now();

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Invoice invoice = (Invoice) o;
		return id.equals(invoice.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
