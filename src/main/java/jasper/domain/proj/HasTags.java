package jasper.domain.proj;

import java.util.List;

public interface HasTags extends HasOrigin {
	String getUrl();
	List<String> getTags();
}
