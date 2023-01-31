package jasper.domain.proj;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public interface HasTags extends HasOrigin {
	String getUrl();
	List<String> getTags();
	void setTags(List<String> tags);
	ObjectNode getPlugins();
	void setPlugins(ObjectNode plugins);
}
