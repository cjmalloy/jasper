package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import jasper.domain.Ref;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class Thumbnail implements Serializable {
	private String url;
	private String color;
	private String emoji;
	private int radius;

	public static Thumbnail getThumbnail(Ref ref) {
		return ref == null ? null : ref.getPlugin("plugin/thumbnail", Thumbnail.class);
	}
}
