package jasper.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jasper.domain.Ref;
import jasper.domain.User;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;


@Getter
@Setter
@JsonInclude(Include.NON_NULL)
public class Pull implements Serializable {
	private String query;
	private int batchSize;
	private boolean validatePlugins;
	private boolean stripInvalidPlugins;
	private boolean validateTemplates;
	private boolean stripInvalidTemplates;
	private String validationOrigin;
	private boolean generateMetadata;
	private List<String> addTags;
	private List<String> removeTags;

	// TODO: copy to origin post-processing
	// TODO: conditional tag/origin mapping

	public void migrate(Ref ref, Origin config) {
		if (ref.getTags() != null && removeTags != null) {
			ref.removePrefixTags();
			migrateTags(ref.getTags(), ref.getPlugins());
		}
		if (addTags != null) {
			ref.addTags(addTags);
		}
		ref.addHierarchicalTags();
	}

	private void migrateTags(List<String> tags, ObjectNode plugins) {
		if (tags == null) return;
		for (int i = tags.size() - 1; i >= 0; i--) {
			var tag = tags.get(i);
			if (removeTags != null && removeTags.contains(tag)) {
				tags.remove(i);
				if (plugins != null) plugins.remove(tag);
			}
		}
	}

	public void migrate(User user, Origin config) {
		migrateTags(user.getReadAccess(), null);
		migrateTags(user.getWriteAccess(), null);
		migrateTags(user.getTagReadAccess(), null);
		migrateTags(user.getTagWriteAccess(), null);
	}

	private static final Pull DEFAULTS = new Pull();
	public static Pull getPull(Ref ref) {
		var pull = ref == null ? null : ref.getPlugin("+plugin/origin/pull", Pull.class);
		return pull == null ? DEFAULTS : pull;
	}
}
