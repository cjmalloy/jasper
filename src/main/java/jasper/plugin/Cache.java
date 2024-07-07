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

import static org.apache.commons.lang3.StringUtils.isBlank;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(Include.NON_NULL)
public class Cache implements Serializable {
	private String id;
	private String mimeType;
	private boolean ban;
	private boolean noStore;
	private boolean thumbnail;
	private Long contentLength;

	public static Cache getCache(Ref ref) {
		return ref == null ? null : ref.getPlugin("_plugin/cache", Cache.class);
	}

	public static boolean bannedOrBroken(Cache cache) {
		if (cache == null) return false;
		return
			// URL has been banned
			cache.isBan() ||
				// If id is blank the last scrape must have failed
				// Wait for the user to manually refresh
				isBlank(cache.getId());
	}
}
