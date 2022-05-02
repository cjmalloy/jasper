package jasper.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import com.vladmihalcea.hibernate.type.search.PostgreSQLTSVectorType;
import jasper.domain.proj.HasTags;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.hibernate.validator.constraints.Length;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static jasper.domain.Origin.ORIGIN_LEN;
import static jasper.domain.TagId.TAG_LEN;

@Entity
@Getter
@Setter
@IdClass(RefId.class)
@TypeDefs({
	@TypeDef(name = "json", typeClass = JsonType.class),
	@TypeDef(name = "tsvector", typeClass = PostgreSQLTSVectorType.class)
})
public class Ref implements HasTags {
	public static final String REGEX = "^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?";
	public static final int URL_LEN = 4096;
	public static final int TITLE_LEN = 512;
	public static final int SEARCH_LEN = 512;

	@Id
	@Column(updatable = false)
	@NotBlank
	@Pattern(regexp = REGEX)
	@Length(max = URL_LEN)
	private String url;

	@Id
	@Column(updatable = false)
	@Pattern(regexp = Origin.REGEX)
	@Length(max = ORIGIN_LEN)
	private String origin = "";

	@Length(max = TITLE_LEN)
	private String title;

	private String comment;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<@Length(max = TAG_LEN) @Pattern(regexp = TagId.REGEX) String> tags;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<@Length(max = URL_LEN) @Pattern(regexp = REGEX) String> sources;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private List<@Length(max = URL_LEN) @Pattern(regexp = REGEX) String> alternateUrls;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private ObjectNode plugins;

	@Type(type = "json")
	@Column(columnDefinition = "jsonb")
	private Metadata metadata;

	@Formula("COALESCE(jsonb_array_length(sources), 0)")
	private String sourceCount;

	@Formula("COALESCE(jsonb_array_length(metadata -> 'responses'), 0)")
	private String responseCount;

	@Formula("COALESCE(jsonb_array_length(metadata -> 'plugins' -> 'plugin/comment'), 0)")
	private String commentCount;

	@Column(updatable = false)
	@NotNull
	private Instant published = Instant.now();

	@CreatedDate
	@Column(updatable = false)
	private Instant created = Instant.now();

	@LastModifiedDate
	private Instant modified = Instant.now();

	@Type(type = "tsvector")
	@Column(updatable = false, insertable = false)
	private String textsearchEn;

	@JsonIgnore
	public Ref addTags(List<String> toAdd) {
		if (toAdd == null) return this;
		if (tags == null) {
			tags = toAdd;
		} else {
			for (var t : toAdd) {
				if (!tags.contains(t)) {
					tags.add(t);
				}
			}
		}
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Ref ref = (Ref) o;
		return url.equals(ref.url) && origin.equals(ref.origin);
	}

	@Override
	public int hashCode() {
		return Objects.hash(url, origin);
	}
}
