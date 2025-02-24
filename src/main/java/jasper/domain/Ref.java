package jasper.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vladmihalcea.hibernate.type.search.PostgreSQLTSVectorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jasper.domain.proj.HasOrigin;
import jasper.domain.proj.HasTags;
import jasper.domain.proj.Tag;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;
import org.hibernate.validator.constraints.Length;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static jasper.config.JacksonConfiguration.om;
import static jasper.domain.proj.Tag.TAG_LEN;
import static jasper.domain.proj.Tag.matchesTag;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Entity
@Getter
@Setter
@IdClass(RefId.class)
public class Ref implements HasTags {
	public static final String REGEX = "^[^:/?#]+:(?://[^/?#]*)?[^?#]*(?:\\?[^#]*)?(?:#.*)?";
	public static final String SCHEME_REGEX = "^[^:/?#]+:";
	public static final int URL_LEN = 4096;
	public static final int TITLE_LEN = 512;

	@Id
	@Column(updatable = false)
	@NotBlank
	@Pattern(regexp = REGEX)
	@Length(max = URL_LEN)
	private String url;

	@Id
	@Column(updatable = false)
	@Pattern(regexp = HasOrigin.REGEX)
	@Length(max = ORIGIN_LEN)
	private String origin = "";

	@Length(max = TITLE_LEN)
	private String title;

	private String comment;

	@JdbcTypeCode(SqlTypes.JSON)
	private List<@Length(max = TAG_LEN) @Pattern(regexp = Tag.REGEX) String> tags;

	@JdbcTypeCode(SqlTypes.JSON)
	private List<@Length(max = URL_LEN) @Pattern(regexp = REGEX) String> sources;

	@JdbcTypeCode(SqlTypes.JSON)
	private List<@Length(max = URL_LEN) @Pattern(regexp = REGEX) String> alternateUrls;

	@JdbcTypeCode(SqlTypes.JSON)
	private ObjectNode plugins;

	@JdbcTypeCode(SqlTypes.JSON)
	private Metadata metadata;

	@Formula("ARRAY_LENGTH(regexp_split_to_array(origin, '.'), 1)")
	@Setter(AccessLevel.NONE)
	private int nesting;

	@Formula("COALESCE(metadata ->> 'modified', to_char(modified, 'YYYY-MM-DD\"T\"HH24:MI:SS.MS\"Z\"'))")
	@Setter(AccessLevel.NONE)
	private String metadataModified;

	@Formula("SUBSTRING(url from 0 for POSITION(':' in url))")
	@Setter(AccessLevel.NONE)
	private String scheme;

	@Formula("metadata -> 'obsolete'")
	@Setter(AccessLevel.NONE)
	private Boolean obsolete;

	@Formula("COALESCE(jsonb_array_length(tags), 0)")
	@Setter(AccessLevel.NONE)
	private String tagCount;

	@Formula("COALESCE(jsonb_array_length(sources), 0)")
	@Setter(AccessLevel.NONE)
	private String sourceCount;

	@Formula("COALESCE(jsonb_array_length(metadata -> 'responses'), 0)")
	@Setter(AccessLevel.NONE)
	private String responseCount;

	@Formula("COALESCE(jsonb_array_length(metadata -> 'plugins' -> 'plugin/comment'), 0)")
	@Setter(AccessLevel.NONE)
	private String commentCount;

	@Formula("COALESCE(jsonb_array_length(metadata -> 'plugins' -> 'plugin/vote/up'), 0) + COALESCE(jsonb_array_length(metadata -> 'plugins' -> 'plugin/vote/down'), 0)")
	private String voteCount;

	@Formula("COALESCE(jsonb_array_length(metadata -> 'plugins' -> 'plugin/vote/up'), 0) - COALESCE(jsonb_array_length(metadata -> 'plugins' -> 'plugin/vote/down'), 0)")
	private String voteScore;

	@Formula("floor((3 + COALESCE(jsonb_array_length(metadata -> 'plugins' -> 'plugin/vote/up'), 0) - COALESCE(jsonb_array_length(metadata -> 'plugins' -> 'plugin/vote/down'), 0)) * pow(CASE WHEN 3 + COALESCE(jsonb_array_length(metadata -> 'plugins' -> 'plugin/vote/up'), 0) > COALESCE(jsonb_array_length(metadata -> 'plugins' -> 'plugin/vote/down'), 0) THEN 0.5 ELSE 2 END, extract(epoch FROM age(published)) / (4 * 60 * 60)))")
	private String voteScoreDecay;

	@Column
	@NotNull
	private Instant published = Instant.now();

	@CreatedDate
	@Column(updatable = false)
	private Instant created = Instant.now();

	private Instant modified = Instant.now();

	@Type(PostgreSQLTSVectorType.class)
	@Column(updatable = false, insertable = false)
	private String textsearchEn;

	public boolean hasPluginResponse(String tag) {
		// TODO: group plugin responses by origin
		if (metadata == null) return false;
		if (metadata.getPlugins() == null) return false;
		return metadata.getPlugins().keySet().stream()
			.filter(t -> matchesTag(tag, t))
			.anyMatch(t -> !metadata.getPlugins().get(t).isEmpty());
	}

	public void setOrigin(String value) {
		origin = value == null ? "" : value;
	}

	public void addHierarchicalTags() {
		addTags(getHierarchicalTags(this.tags));
	}

	public void removePrefixTags() {
		removePrefixTags(tags);
	}

	@JsonIgnore
	public Ref removeTag(String tag) {
		if (tags == null || isBlank(tag)) return this;
		for (int i = tags.size() - 1; i >= 0; i--) {
			var remove = tags.get(i);
			if (matchesTag(tag, remove)) {
				tags.remove(i);
				setPlugin(remove, null);
			}
		}
		if (plugins != null) {
			var remove = new ArrayList<String>();
			plugins.fieldNames().forEachRemaining(p -> {
				if (!hasTag(p)) remove.add(p);
			});
			for (var p : remove) setPlugin(p, null);
		}
		return this;
	}

	@JsonIgnore
	public Ref removeTags(List<String> toRemove) {
		if (tags == null || toRemove == null) return this;
		for (var r : toRemove) removeTag(r);
		return this;
	}

	@JsonIgnore
	public Ref addTag(String tag) {
		if (isBlank(tag)) return this;
		if (isBlank(tag)) return this;
		if (tags == null) {
			if (tag.startsWith("-")) return this;
			tags = new ArrayList<>();
			tags.add(tag);
		} else {
			if (tag.startsWith("-")) {
				removeTag(tag.substring(1));
			} else if (!tags.contains(tag)) {
				tags.add(tag);
			}
		}
		return this;
	}

	@JsonIgnore
	public Ref addTags(List<String> toAdd) {
		if (toAdd == null) return this;
		for (var t : toAdd) addTag(t);
		return this;
	}

	@JsonIgnore
	public Ref addPlugins(List<String> toAdd, ObjectNode from) {
		if (toAdd == null || from == null) return this;
		for (var t : toAdd) {
			if (from.has(t)) setPlugin(t, from.get(t));
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

	public static List<String> getHierarchicalTags(List<String> tags) {
		if (tags == null) return new ArrayList<>();
		var result = new ArrayList<>(tags);
		for (var i = result.size() - 1; i >= 0; i--) {
			var t = result.get(i);
			while (t.contains("/")) {
				t = t.substring(0, t.lastIndexOf("/"));
				if (!result.contains(t)) {
					result.add(t);
				}
			}
		}
		return result;
	}

	public static void removePrefixTags(List<String> tags) {
		if (tags == null) return;
		for (int i = tags.size() - 1; i >= 0; i--) {
			var check = tags.get(i) + "/";
			for (int j = 0; j < tags.size(); j++) {
				if (tags.get(j).startsWith(check)) {
					tags.remove(i);
					break;
				}
			}
		}
	}

	@JsonIgnore
	public Ref setPlugin(String tag, Object jsonNode) {
		if (jsonNode == null) {
			if (plugins != null) plugins.remove(tag);
			return this;
		}
		if (plugins == null) plugins = om().createObjectNode();
		addTag(tag);
		plugins.set(tag, om().convertValue(jsonNode, JsonNode.class));
		return this;
	}

	@JsonIgnore
	public boolean hasPlugin(String tag) {
		if (plugins == null) return false;
		if (!plugins.has(tag)) return false;
		return plugins.get(tag) != null;
	}

	@JsonIgnore
	public JsonNode getPlugin(String tag) {
		if (plugins == null) return null;
		if (!plugins.has(tag)) return null;
		return plugins.get(tag);
	}

	@JsonIgnore
	public <T> T getPlugin(String tag, Class<T> toValueType) {
		if (plugins == null) return null;
		if (!plugins.has(tag)) return null;
        return om().convertValue(plugins.get(tag), toValueType);
	}

	@JsonIgnore
	public int getPluginResponses(String tag) {
		if (metadata == null) return 0;
		if (metadata.getPlugins() == null) return 0;
		if (!metadata.getPlugins().containsKey(tag)) return 0;
		if (metadata.getPlugins().get(tag) == null) return 0;
		return metadata.getPlugins().get(tag).size();
	}

	@JsonIgnore
	public boolean hasTag(String ...tag) {
		if (tags == null) return false;
		return Arrays.stream(tag).allMatch(m -> tags.stream().anyMatch(t -> matchesTag(m, t)));
	}

	public static Ref from(String url, String origin, String ...tags) {
		var result = new Ref();
		result.setUrl(url);
		result.setOrigin(origin);
		for (var tag : tags) result.addTag(tag);
		return result;
	}
}
