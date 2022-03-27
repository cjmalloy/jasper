package ca.hc.jasper.domain;

import java.time.Instant;
import java.util.*;
import javax.persistence.Entity;
import javax.persistence.*;
import javax.validation.constraints.Pattern;

import com.vladmihalcea.hibernate.type.json.JsonType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;
import org.hibernate.validator.constraints.URL;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@Setter
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class)
})
public class Invoice {

	@Id
	@Column(updatable = false)
	private UUID id;

	@Column(updatable = false)
	@Pattern(regexp = TagId.REGEX)
	private String submitter;

	@Column(updatable = false)
	private String comment;

	@Column(updatable = false)
	@Pattern(regexp = TagId.REGEX)
	private String queue;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb", updatable = false)
	private List<@URL String> response;

	@Column(updatable = false)
	private String qr;

	private boolean paid;

	private boolean rejected;

	private boolean disputed;

	@LastModifiedDate
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
